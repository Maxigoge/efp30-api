# Bootstrap del primer administrador

La aplicación puede crear el primer ADMIN al arrancar, una sola vez. Antes del
primer inicio configura estos valores en el entorno del proceso:

- `BOOTSTRAP_ADMIN_EMAIL`: email válido de hasta 254 caracteres.
- `BOOTSTRAP_ADMIN_NAME`: nombre visible de hasta 120 caracteres.
- `BOOTSTRAP_ADMIN_PASSWORD`: entre 12 y 128 caracteres.

Si las tres variables están vacías, no se crea una cuenta. Si falta alguna o no
cumple las reglas, la aplicación falla al iniciar para evitar una cuenta
parcial. Cuando ya existe un ADMIN habilitado, los valores no cambian su cuenta
ni su contraseña. La contraseña se guarda como hash BCrypt; el proceso nunca la
registra en logs. Retira las variables del entorno una vez creado el usuario.

Configura las variables en el gestor de secretos o entorno de ejecución de la
infraestructura. No las guardes en `.env`, Dockerfile, archivos de configuración
versionados ni argumentos de una imagen.

En producción establece `SESSION_COOKIE_SECURE=true`. La cookie es
`EFP30_SESSION`, `HttpOnly`, `SameSite=Lax` y host-only; la API no establece un
dominio. El despliegue debe servir HTTPS desde Cloudflare Tunnel hasta el
gateway y reenviar correctamente los encabezados del proxy.

El administrador pide `GET /api/v1/auth/csrf` antes del login y de cada
escritura; manda el valor en `X-CSRF-TOKEN`. Después del login y logout vuelve a
pedir el token. Cinco fallos de login en 15 minutos bloquean temporalmente el
par IP/cuenta y responden 429 con `Retry-After`.

ADMIN gestiona usuarios y datos institucionales. ADMIN y EDITOR gestionan el
contenido editorial y los medios. El sistema impide deshabilitar o quitar el
rol al último ADMIN activo.
