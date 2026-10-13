# Operación de producción

La API se ejecuta detrás de Cloudflare Tunnel y un gateway Nginx. El túnel sólo
publica Nginx; Nginx sirve el frontend del host y enruta `/api/v1/` a la API.
La API no publica un puerto al host en producción. PostgreSQL comparte una red
privada sólo con la API. Nginx no se conecta a PostgreSQL. El volumen de medios
sólo se monta en la API.

La imagen propia usa Java 21, compila y prueba con Gradle Wrapper en la primera
etapa y ejecuta el JAR en una imagen JRE. El proceso corre como UID/GID 10001 y
escucha en 8080. La etiqueta de Temurin queda fijada a la publicación Java
21.0.12.1_1 sobre Ubuntu Noble; actualizala al revisar nuevos parches.

Construcción local:

    docker build -t efp30-api:local .

Ejecutá las verificaciones del repositorio antes de construir la imagen. Para
producción montá un volumen persistente en `/var/lib/efp30/media`; PostgreSQL
usa su propio volumen y se administra aparte. La imagen no incorpora datos,
credenciales, base de datos ni archivos de medios. Respaldá PostgreSQL y el
volumen de medios juntos para conservar referencias a los archivos.

La API requiere `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`.
Configura los tres mediante el gestor de secretos del entorno, además de
`MEDIA_ROOT=/var/lib/efp30/media`, `PORT=8080` y
`SESSION_COOKIE_SECURE=true`. Para el primer arranque, inyectá también las tres
variables de bootstrap descritas en [bootstrap-admin.md](bootstrap-admin.md).
Retiralas tras crear la cuenta; no las agregues a Git, a la imagen o a los
argumentos de build.

Ejemplo de redes Docker, volúmenes y variables (los valores se inyectan desde
el entorno seguro):

    docker network create efp30-edge
    docker network create --internal efp30-app
    docker network create --internal efp30-db
    docker volume create efp30-media
    docker run -d --name efp30-api --network efp30-db --network-alias efp30-api \
      --mount type=volume,src=efp30-media,dst=/var/lib/efp30/media \
      --env DATABASE_URL --env DATABASE_USERNAME --env DATABASE_PASSWORD \
      --env MEDIA_ROOT=/var/lib/efp30/media --env PORT=8080 \
      --env SESSION_COOKIE_SECURE=true efp30-api:local
    docker network connect --alias efp30-api efp30-app efp30-api

Conectá PostgreSQL sólo a `efp30-db`. Conectá Nginx a `efp30-edge` y
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
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_request_buffering on;
        proxy_read_timeout 60s;
        proxy_pass http://efp30-api:8080;
    }

Restringí el origen Nginx a Cloudflare Tunnel, permití sólo los hosts de la
institución y reemplazá los encabezados reenviados del cliente. El encabezado
`X-Forwarded-Proto` debe reflejar HTTPS público para que Spring configure la
cookie de sesión segura. Los navegadores llaman `/api/v1` desde el mismo host
del frontend, por lo que no se configura CORS.

Los probes son `/actuator/health/liveness` y
`/actuator/health/readiness`; Nginx y el orquestador pueden consultarlos. El
limitador de login es local al proceso; en un despliegue con varias réplicas,
configurá además un límite compartido en el gateway.

La API aplica Flyway al arrancar. La disponibilidad exige una base alcanzable
con permisos para migrar y una fila institucional inicial cargada por ADMIN;
la lectura pública de institución informa 503 hasta que exista. No se incluye
comando de despliegue ni se publica esta imagen desde el repositorio.
