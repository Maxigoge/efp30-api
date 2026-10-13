# EFP N°30 API

Repositorio independiente de la API institucional y administrativa de EFP N°30.
El contrato OpenAPI v1 se prepara antes del backend y es la fuente oficial para
los repositorios institucional y administración. Campus queda fuera de este
alcance.

## Estado

| Hito | Alcance | Estado |
|---|---|---|
| A0 | Contrato OpenAPI, ejemplos, fixtures y mock HTTP | Completo |
| A1 | Proyecto Java, PostgreSQL, Flyway y health | Completo |
| A2 | Lectura pública | Completo |
| A3 | Sesiones, edición y medios | Completo |
| A4 | Integración, documentación e imagen Docker | Pendiente |

## Contrato y mock

El documento oficial está en docs/contratos/openapi-v1.yaml. Las decisiones,
compatibilidad y convenciones se explican en docs/contratos/README.md. Cada
frontend debe copiar el contrato desde un SHA de este repositorio y registrar
ese SHA junto a su copia. La revisión v1 vigente recomendada es `86682e93ab108f2484a75aa73f4888ecc72555b7`.

Los fixtures de fixtures/editorial.json y los ejemplos JSON son ficticios.
El mock local responde bajo el mismo prefijo que usará el backend:

    npm run mock

Los comandos de verificación de A0 son:

    npm run validate:openapi
    npm run test:mock

El mock escucha sólo en 127.0.0.1:4010 por defecto. No conecta a PostgreSQL,
no persiste los cambios y no es un servicio de producción.

## Stack de API

La implementación se inicializa en A1 con Java 21, Spring Boot, Spring Security,
PostgreSQL y Flyway. Las versiones precisas de plugins, dependencias, driver e
imagen de base se fijarán juntas en ese hito y se comprobarán contra Java 21.

La topología prevista es Cloudflare Tunnel → gateway Nginx → API. Los
frontends llaman /api/v1 en su propio host. PostgreSQL y los archivos sólo
serán accesibles desde la API. No se despliega desde este repositorio.

## Backend A1

Se fijaron Java 21, Spring Boot 3.5.16, Gradle 8.14.4 y PostgreSQL 18.6; los
componentes Spring, Flyway y el driver JDBC siguen el BOM de Spring Boot. El
checksum del wrapper y las referencias de compatibilidad están en
docs/arquitectura/versiones.md.

La aplicación escucha en 8080 por defecto. Configura `DATABASE_URL`,
`DATABASE_USERNAME` y `DATABASE_PASSWORD`; Flyway aplica las migraciones al
arrancar y Hibernate valida el esquema sin modificarlo. No se guardan bytes de
archivos en PostgreSQL: el esquema conserva sus metadatos y la clave opaca del
almacenamiento que se implementará en A3.

Los probes son `/actuator/health/liveness` y
`/actuator/health/readiness`. El primero informa vida del proceso; el segundo
incluye el estado de la base. Sólo se expone health y sus respuestas no revelan
detalles de los componentes.

En PowerShell, con JDK 21 disponible:

    .\gradlew.bat test
    .\gradlew.bat bootRun

`test` usa H2 para levantar la aplicación y probar los probes. La migración se
valida con PostgreSQL 18.6 mediante Testcontainers cuando hay Docker; en un
entorno sin runtime de contenedores esa prueba queda omitida explícitamente.

## Lectura pública A2

Las rutas `/api/v1/public` consultan noticias, cursos, familias, eventos,
información institucional, carrusel y medios. Las listas usan paginación base
cero (`page=0`, `size=20`, máximo 100) y los filtros definidos en OpenAPI.
Noticias, cursos y eventos se ordenan de forma estable; el calendario usa
intervalos semiabiertos `[from,to)` y serializa instantes con el offset de
`America/Argentina/Buenos_Aires`.

Las consultas públicas filtran borradores y archivos en SQL. Cursos requieren
familia activa. La portada de curso se obtiene de su `coverMediaId`, separada
de la galería; la galería y carrusel omiten fotos o medios deshabilitados,
privados y no fotográficos. El contenido binario se resuelve bajo
`MEDIA_ROOT` (por defecto `./data/media`) con claves opacas y sin rutas físicas
en respuestas.

Las pruebas HTTP usan fixtures ficticios sobre H2 para cubrir filtros,
paginación, aislamiento editorial, calendario, carrusel, archivo público y
404 uniforme para medios privados o deshabilitados. La fila institucional no
se inventa: hasta que ADMIN la configure, la lectura pública responde 503 según
el contrato vigente.

El esquema inicial persiste usuarios ADMIN/EDITOR, contenido editorial,
familias, cursos y requisitos, galerías y selección de carrusel, eventos con
instantes y zona IANA, información institucional, metadatos de medios y tablas
de Spring Session JDBC. La portada del curso está en una FK independiente de
las fotos de galería. No incluye entidades de campus.

La información institucional real y sus datos de contacto todavía deben
facilitarse para completar la página pública; hasta entonces, A2 devuelve 503 y
no inserta datos ficticios. ADMIN podrá configurar esa fila en A3.

### Edición administrativa A3b

ADMIN y EDITOR pueden crear, editar, listar, publicar y archivar noticias,
cursos y eventos; también gestionan familias y galerías. Los cambios comparan
`version` y responden `409 VERSION_CONFLICT` cuando el registro cambió. La
portada del curso se mantiene aparte de su galería. La institución se configura
sólo por ADMIN y responde 503 hasta el primer `PUT` con `version=0`. Los eventos
validan el offset con `America/Argentina/Buenos_Aires`; día completo usa
medianoche local y fin exclusivo.

### Medios A3c

La carga multipart acepta sólo JPEG, PNG y WebP hasta 5 MiB, o PDF hasta 10 MiB.
El servidor compara la firma binaria con la extensión y el tipo declarado,
rechaza rutas del cliente y guarda cada objeto con una clave UUID más extensión
bajo `MEDIA_ROOT`; PostgreSQL conserva metadatos y SHA-256. La lectura pública
requiere visibilidad PUBLIC y medio habilitado. Los PRIVATE sólo se descargan
con sesión ADMIN/EDITOR, como adjunto, con `nosniff` y `Cache-Control: private,
no-store`. Al volver privado un medio referenciado, la API responde 409.

El primer administrador se crea mediante el procedimiento idempotente de
docs/operaciones/bootstrap-admin.md; ningún usuario ni contraseña inicial se
guarda en Git.

## Alcance v1

Incluye noticias, cursos, familias profesionales, calendario, información
institucional, usuarios administrativos ADMIN/EDITOR, sesiones, CSRF, archivos
validados, galerías de curso y selección del carrusel de portada.

No incluye campus, matrículas, aulas, actividades, entregas ni calificaciones.
