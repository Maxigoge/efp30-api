# Contrato oficial v1

openapi-v1.yaml es la fuente oficial del contrato HTTP de EFP N°30. La web
institucional y administración deben copiar ese archivo y registrar el SHA del
commit de API del que salió su copia. Los cambios compatibles se agregan a v1;
un cambio incompatible requiere documentar el motivo, preparar una nueva
versión y coordinar la actualización de ambos consumidores antes de liberar la
API. No se modifica el contrato desde los repositorios frontend.

La versión inicial que deben copiar los consumidores está fijada en el commit
`b71daa7405e485d54a2fa02fb6d93d558f7afdc1`.

Validación:

    npm run validate:openapi

La base es /api/v1 y los navegadores usan ese prefijo en su propio host. JSON
usa camelCase, fechas ISO 8601 con offset y UUID. La zona IANA para eventos es
America/Argentina/Buenos_Aires. Paginación desde cero: page=0, size=20 por
defecto y máximo 100. Las listas devuelven
{items,page,size,totalItems,totalPages}. El orden de cada lista y los filtros
están descritos junto a la operación OpenAPI.

Los estados editoriales son DRAFT, PUBLISHED y ARCHIVED. Crear inicia un
borrador; editar conserva estado y slug; publicar valida los campos obligatorios
y asigna publishedAt en el servidor. DRAFT puede publicarse o archivarse;
PUBLISHED puede archivarse; ARCHIVED es terminal en v1. Archivar retira el
contenido de las rutas públicas. No hay rutas públicas para borradores o
archivos. La portada
coverMediaId de un curso es independiente de photos. El carrusel agrega fotos
de galería habilitadas y seleccionadas, sólo de cursos publicados y medios
públicos habilitados; se ordena por position, courseId e id. Un curso archivado
o una asociación de foto deshabilitada deja de aparecer.

Las solicitudes de edición incluyen version. Una versión vigente produce otra
versión; una obsoleta responde 409 con code=VERSION_CONFLICT. Las creaciones
editoriales comienzan con versión cero. Los errores usan
application/problem+json y contienen type, title, status, detail, instance,
code y fieldErrors; nunca incluyen stacktrace.

La sesión administrativa usa cookie EFP30_SESSION, host-only, HttpOnly, Secure
en producción y SameSite=Lax. El frontend pide /auth/csrf antes del login y de
cada escritura, y envía X-CSRF-TOKEN. Vuelve a pedir token después de login y
logout. ADMIN y EDITOR gestionan contenido editorial, medios, familias y
galerías. Sólo ADMIN gestiona usuarios y datos institucionales. Los roles
académicos y las rutas de campus no existen en v1.

Las rutas públicas sólo devuelven recursos publicados y archivos PUBLIC
habilitados. Un archivo PRIVATE, deshabilitado o inexistente responde igual
desde rutas públicas. Administración obtiene bytes privados por una ruta
protegida con Cache-Control: private, no-store. El servidor identifica el tipo
desde los bytes: JPEG, PNG o WebP hasta 5 MiB; PDF hasta 10 MiB; sin SVG, HTML
ni rutas proporcionadas por el cliente.

Los fixtures en ../../fixtures/editorial.json son datos ficticios. El mock en
../../mocks implementa las rutas para desarrollo local, maneja sesión, roles,
CSRF, filtros, paginación, transiciones editoriales, versiones y validación
básica de medios. Sólo para los emails ficticios que figuran en los fixtures,
acepta cualquier contraseña no vacía; no hay una contraseña demo guardada.
