# EFP N°30 API

Repositorio independiente de la API institucional y administrativa de EFP N°30.
El contrato OpenAPI v1 se prepara antes del backend y es la fuente oficial para
los repositorios institucional y administración. Campus queda fuera de este
alcance.

## Estado

| Hito | Alcance | Estado |
|---|---|---|
| A0 | Contrato OpenAPI, ejemplos, fixtures y mock HTTP | Completo |
| A1 | Proyecto Java, PostgreSQL, Flyway y health | Pendiente |
| A2 | Lectura pública | Pendiente |
| A3 | Sesiones, edición y medios | Pendiente |
| A4 | Integración, documentación e imagen Docker | Pendiente |

## Contrato y mock

El documento oficial está en docs/contratos/openapi-v1.yaml. Las decisiones,
compatibilidad y convenciones se explican en docs/contratos/README.md. Cada
frontend debe copiar el contrato desde un SHA de este repositorio y registrar
ese SHA junto a su copia.

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

El primer administrador se crea mediante un procedimiento operativo idempotente
documentado en A3; ningún usuario ni contraseña inicial se guarda en Git.

## Alcance v1

Incluye noticias, cursos, familias profesionales, calendario, información
institucional, usuarios administrativos ADMIN/EDITOR, sesiones, CSRF, archivos
validados, galerías de curso y selección del carrusel de portada.

No incluye campus, matrículas, aulas, actividades, entregas ni calificaciones.
