# Operación de producción

La API se ejecuta detrás de Cloudflare Tunnel y un gateway Nginx. El túnel sólo
publica Nginx; Nginx sirve el frontend del host y enruta `/api/v1/` a la API.
La API no publica un puerto al host en producción. MySQL 8.4.11 comparte una red
privada sólo con la API. Nginx no se conecta a MySQL. El volumen de medios sólo
se monta en la API.

La imagen propia usa Java 21, compila y prueba con Gradle Wrapper en la primera
etapa y ejecuta el JAR en una imagen JRE. El proceso corre como UID/GID 10001 y
escucha en 8080. La etiqueta de Temurin queda fijada a la publicación Java
21.0.12.1_1 sobre Ubuntu Noble; actualizala al revisar nuevos parches.

Construcción local:

    docker build -t efp30-api:local .

Ejecutá las verificaciones del repositorio antes de construir la imagen. Para
producción montá un volumen persistente en `/var/lib/efp30/media` y un volumen
de datos persistente en MySQL. La imagen de API no incorpora datos, secretos,
base de datos ni archivos de medios. Respaldá MySQL y el volumen de medios
juntos para conservar referencias a los archivos.

La API acepta estas variables:

- `DATABASE_URL`: por ejemplo,
  `jdbc:mysql://efp30-db:3306/efp30?connectionTimeZone=%2B00%3A00&forceConnectionTimeZoneToSession=true&preserveInstants=true`.
- `DATABASE_USERNAME`: usuario de aplicación limitado a su esquema.
- `DATABASE_PASSWORD` o `DATABASE_PASSWORD_FILE`: contraseña de base, usando
  una sola opción. Con `DATABASE_PASSWORD_FILE=/run/secrets/mysql_app_password`,
  la API lee el archivo UTF-8 al iniciar, admite un salto de línea final, no lo
  registra, falla si no puede leerlo y limita su tamaño a 4096 bytes. El montaje
  del secreto debe ser de sólo lectura y el usuario 10001 debe poder leerlo.
- `MEDIA_ROOT=/var/lib/efp30/media`, `PORT=8080` y
  `SESSION_COOKIE_SECURE=true`.
- `TRUSTED_PROXY_PATTERN=^172\.30\.33\.2$`: expresión regular Java de la IP
  del gateway Nginx. Spring Boot usa el procesamiento nativo de Tomcat y sólo
  considera `X-Forwarded-*` cuando la conexión viene de una IP que coincide con
  esta expresión. Tomcat espera una expresión regular, no CIDR; el valor por
  defecto está anclado a `172.30.33.2`.

Para el primer arranque, inyectá también las tres variables de bootstrap
descritas en [bootstrap-admin.md](bootstrap-admin.md). Retiralas tras crear la
cuenta; no las agregues a Git, a la imagen o a argumentos de build.

Flyway aplica las migraciones versionadas automáticamente al arrancar la API,
antes de aceptar tráfico. Hibernate valida las tablas y no cambia el esquema.
Cada migración nueva debe ser aditiva cuando sea posible y quedar probada con
MySQL 8.4.11. La prueba de integración ejecuta las dos migraciones y una segunda
ejecución para comprobar que Flyway no repite cambios.

Ejemplo de redes Docker, volúmenes y secretos (los valores se inyectan desde el
entorno seguro):

    docker network create efp30-edge
    docker network create --internal efp30-app
    docker network create --internal efp30-db
    docker volume create efp30-media
    docker volume create efp30-mysql-data
    docker run -d --name efp30-db --network efp30-db --network-alias efp30-db \
      --mount type=volume,src=efp30-mysql-data,dst=/var/lib/mysql \
      --env MYSQL_DATABASE=efp30 --env MYSQL_USER=efp30 \
      --env MYSQL_PASSWORD_FILE=/run/secrets/mysql_app_password \
      --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql_root_password \
      --mount type=bind,src=/secure/secrets/mysql_app_password,dst=/run/secrets/mysql_app_password,readonly \
      --mount type=bind,src=/secure/secrets/mysql_root_password,dst=/run/secrets/mysql_root_password,readonly \
      mysql:8.4.11
    docker run -d --name efp30-api --network efp30-db --network-alias efp30-api \
      --mount type=volume,src=efp30-media,dst=/var/lib/efp30/media \
      --mount type=bind,src=/secure/secrets/mysql_app_password,dst=/run/secrets/mysql_app_password,readonly \
      --env DATABASE_URL='jdbc:mysql://efp30-db:3306/efp30?connectionTimeZone=%2B00%3A00&forceConnectionTimeZoneToSession=true&preserveInstants=true' \
      --env DATABASE_USERNAME=efp30 --env DATABASE_PASSWORD_FILE=/run/secrets/mysql_app_password \
      --env 'TRUSTED_PROXY_PATTERN=^172\.30\.33\.2$' \
      --env MEDIA_ROOT=/var/lib/efp30/media --env PORT=8080 \
      --env SESSION_COOKIE_SECURE=true efp30-api:local
    docker network connect --alias efp30-api efp30-app efp30-api

Usá el mismo secreto de aplicación en MySQL y en la API. En producción, un
orquestador de secretos como Docker Swarm Secrets debe reemplazar los bind mounts
de ejemplo. Conectá MySQL sólo a `efp30-db`. Conectá Nginx a `efp30-edge` y
`efp30-app`, y Cloudflare Tunnel sólo a `efp30-edge`. La API está en
`efp30-db` y `efp30-app`; así Nginx no alcanza la base. Nginx no monta el
volumen de medios. La API no se publica en el host.

Fragmento Nginx para conservar el prefijo API:

    location /api/v1/ {
        client_max_body_size 11m;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_request_buffering on;
        proxy_read_timeout 60s;
        proxy_pass http://efp30-api:8080;
    }

Restringí el origen Nginx a Cloudflare Tunnel, permití sólo los hosts de la
institución y reemplazá los encabezados reenviados del cliente. El encabezado
`X-Forwarded-For` debe ser la dirección de cliente que Nginx validó desde
Cloudflare; no agregues el valor que envió el cliente. `X-Forwarded-Proto` debe
reflejar HTTPS público para que Spring configure la cookie de sesión segura.
Tomcat ignora esos encabezados cuando la conexión no viene de la IP configurada
en `TRUSTED_PROXY_PATTERN`. Los navegadores llaman `/api/v1` desde el mismo host
del frontend, por lo que no se configura CORS.

Los probes son `/actuator/health/liveness` y
`/actuator/health/readiness`; readiness consulta la conexión MySQL. La
disponibilidad exige una base alcanzable con permisos para migrar y una fila
institucional inicial cargada por ADMIN; la lectura pública de institución
informa 503 hasta que exista. No se incluye comando de despliegue ni se publica
esta imagen desde el repositorio.
