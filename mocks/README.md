# Mock HTTP local

Desde la raíz de efp30-api:

    npm run mock

Escucha en http://127.0.0.1:4010/api/v1. Se puede cambiar el puerto con
MOCK_PORT. /health devuelve {"status":"UP"}. El estado mutado vive en memoria y
vuelve a los fixtures al reiniciar.

Los dos frontends pueden conservar llamadas relativas a /api/v1 y apuntar su
proxy de desarrollo a http://127.0.0.1:4010. Por ejemplo, el proxy de Vite
reenvía el prefijo sin cambiarlo. El mock no requiere CORS ni conexión a una
base de datos y no debe configurarse como servicio de producción.

Los usuarios admin@efp30.test y editora@efp30.test son identidades ficticias
del fixture. El mock acepta cualquier contraseña no vacía para esos emails;
no hay credenciales reales ni una contraseña demo almacenada. Las escrituras
requieren primero GET /api/v1/auth/csrf y luego el header X-CSRF-TOKEN. Después
de login hay que pedir un token nuevo.

Ejecutar las comprobaciones locales con:

    npm run test:mock
