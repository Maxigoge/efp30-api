import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import {createMockServer} from './server.mjs';

test('fixtures y ejemplos son JSON ficticio y no contienen contraseñas guardadas', async () => {
  const fixture = JSON.parse(await readFile(new URL('../fixtures/editorial.json', import.meta.url), 'utf8'));
  assert.match(fixture.fixtureMetadata.purpose, /ficticios/);
  assert.equal(fixture.users.some((user) => 'password' in user || 'passwordHash' in user), false);
  const examplesDir = new URL('../docs/ejemplos/', import.meta.url);
  const names = await readdir(examplesDir);
  assert.ok(names.length >= 4);
  for (const name of names.filter((entry) => entry.endsWith('.json'))) JSON.parse(await readFile(new URL(name, examplesDir), 'utf8'));
});

async function start(t) {
  const server = createMockServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  t.after(() => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())));
  return 'http://127.0.0.1:' + address.port;
}

async function call(base, path, options = {}, session) {
  const headers = new Headers(options.headers ?? {});
  if (session?.cookie) headers.set('Cookie', session.cookie);
  if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
    options = {...options, body: JSON.stringify(options.body)};
  }
  const response = await fetch(base + path, {...options, headers});
  const setCookie = response.headers.get('set-cookie');
  if (setCookie && session) session.cookie = setCookie.split(';')[0];
  return response;
}

async function signIn(base, email = 'editora@efp30.test') {
  const session = {cookie: ''};
  const csrfResponse = await call(base, '/api/v1/auth/csrf', {}, session);
  const csrf = (await csrfResponse.json()).token;
  const login = await call(base, '/api/v1/auth/login', {
    method: 'POST',
    headers: {'X-CSRF-TOKEN': csrf},
    body: {email, password: 'any-non-empty-mock-value'}
  }, session);
  assert.equal(login.status, 200);
  const refreshed = await call(base, '/api/v1/auth/csrf', {}, session);
  session.csrf = (await refreshed.json()).token;
  return session;
}

test('la lectura pública oculta borradores y los filtros usan paginación', async (t) => {
  const base = await start(t);
  const list = await call(base, '/api/v1/public/noticias?page=0&size=1');
  const page = await list.json();
  assert.equal(list.status, 200);
  assert.equal(page.totalItems, 1);
  assert.equal(page.items[0].slug, 'inscripciones-a-talleres');
  assert.equal('status' in page.items[0], false);
  assert.equal((await call(base, '/api/v1/public/noticias/muestra-anual')).status, 404);
  assert.equal((await call(base, '/api/v1/public/cursos/panaderia-artesanal')).status, 404);
  assert.equal((await call(base, '/api/v1/public/eventos/borrador-de-evento')).status, 404);
});

test('sesión, roles y CSRF protegen las operaciones administrativas', async (t) => {
  const base = await start(t);
  assert.equal((await call(base, '/api/v1/admin/noticias')).status, 401);
  const editor = await signIn(base);
  assert.equal((await call(base, '/api/v1/admin/noticias', {}, editor)).status, 200);
  assert.equal((await call(base, '/api/v1/admin/users', {}, editor)).status, 403);
  assert.equal((await call(base, '/api/v1/admin/institucion', {}, editor)).status, 403);
  const noCsrf = await call(base, '/api/v1/admin/noticias', {method: 'POST', body: {}}, editor);
  assert.equal(noCsrf.status, 403);
  const me = await call(base, '/api/v1/auth/me', {}, editor);
  assert.equal((await me.json()).user.roles[0], 'EDITOR');
  const logout = await call(base, '/api/v1/auth/logout', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}}, editor);
  assert.equal(logout.status, 204);
  assert.equal((await call(base, '/api/v1/auth/me', {}, editor)).status, 401);
});

test('crear, publicar y archivar noticia cambia su visibilidad pública', async (t) => {
  const base = await start(t);
  const editor = await signIn(base);
  const created = await call(base, '/api/v1/admin/noticias', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: {
    slug: 'aviso-ficticio', title: 'Aviso ficticio', summary: 'Resumen de prueba.',
    bodyMarkdown: 'Texto de prueba.', coverMediaId: null, category: 'Institucional', featured: false
  }}, editor);
  const draft = await created.json();
  assert.equal(created.status, 201);
  assert.equal(draft.status, 'DRAFT');
  assert.equal((await call(base, '/api/v1/public/noticias/aviso-ficticio')).status, 404);
  const publish = await call(base, '/api/v1/admin/noticias/' + draft.id + '/publish', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: {version: 0}}, editor);
  assert.equal((await publish.json()).status, 'PUBLISHED');
  assert.equal((await call(base, '/api/v1/public/noticias/aviso-ficticio')).status, 200);
  const archive = await call(base, '/api/v1/admin/noticias/' + draft.id + '/archive', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: {version: 1}}, editor);
  assert.equal((await archive.json()).status, 'ARCHIVED');
  assert.equal((await call(base, '/api/v1/public/noticias/aviso-ficticio')).status, 404);
});

test('versiones antiguas responden 409 y no pisan cambios recientes', async (t) => {
  const base = await start(t);
  const editor = await signIn(base);
  const before = await call(base, '/api/v1/admin/noticias/11111111-1111-4111-8111-111111111102', {}, editor);
  const draft = await before.json();
  const body = {version: draft.version, title: draft.title, summary: draft.summary, bodyMarkdown: draft.bodyMarkdown, coverMediaId: null, category: draft.category, featured: false};
  const first = await call(base, '/api/v1/admin/noticias/' + draft.id, {method: 'PUT', headers: {'X-CSRF-TOKEN': editor.csrf}, body}, editor);
  assert.equal(first.status, 200);
  const conflict = await call(base, '/api/v1/admin/noticias/' + draft.id, {method: 'PUT', headers: {'X-CSRF-TOKEN': editor.csrf}, body}, editor);
  assert.equal(conflict.status, 409);
  assert.equal((await conflict.json()).code, 'VERSION_CONFLICT');
});

test('calendario incluye solapamientos y respeta fin exclusivo de día completo', async (t) => {
  const base = await start(t);
  const overlap = await call(base, '/api/v1/public/eventos?from=2026-10-31T00%3A00%3A00-03%3A00&to=2026-11-01T00%3A00%3A00-03%3A00');
  const page = await overlap.json();
  assert.equal(page.totalItems, 1);
  assert.equal(page.items[0].slug, 'feria-de-oficios');
  const boundary = await call(base, '/api/v1/public/eventos?from=2026-11-02T00%3A00%3A00-03%3A00&to=2026-11-03T00%3A00%3A00-03%3A00');
  assert.equal((await boundary.json()).totalItems, 0);
  assert.equal((await call(base, '/api/v1/public/eventos?from=bad&to=bad')).status, 400);
});

test('el carrusel retira fotos deshabilitadas y cursos archivados', async (t) => {
  const base = await start(t);
  const editor = await signIn(base);
  const initial = await (await call(base, '/api/v1/public/portada/carrusel')).json();
  assert.deepEqual(initial.items.map((item) => item.id), ['55555555-5555-4555-8555-555555555501']);
  const courseId = '33333333-3333-4333-8333-333333333301';
  const galleryResponse = await call(base, '/api/v1/admin/cursos/' + courseId + '/photos', {}, editor);
  const gallery = await galleryResponse.json();
  const disabled = gallery.photos.map((photo) => ({...photo, showOnHomepage: false}));
  const saved = await call(base, '/api/v1/admin/cursos/' + courseId + '/photos', {method: 'PUT', headers: {'X-CSRF-TOKEN': editor.csrf}, body: {version: gallery.version, photos: disabled}}, editor);
  assert.equal(saved.status, 200);
  assert.deepEqual((await (await call(base, '/api/v1/public/portada/carrusel')).json()).items, []);
  const publicCourse = await (await call(base, '/api/v1/public/cursos/electricidad-domiciliaria')).json();
  assert.equal(publicCourse.coverUrl, '/api/v1/public/media/66666666-6666-4666-8666-666666666603');
  assert.equal(publicCourse.photos[0].imageUrl, '/api/v1/public/media/66666666-6666-4666-8666-666666666601');
  const archived = await call(base, '/api/v1/admin/cursos/' + courseId + '/archive', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: {version: 2}}, editor);
  assert.equal((await archived.json()).status, 'ARCHIVED');
  assert.equal((await call(base, '/api/v1/public/cursos/electricidad-domiciliaria')).status, 404);
});

test('medios privados no son públicos y la carga valida bytes, extensión y tamaño', async (t) => {
  const base = await start(t);
  const editor = await signIn(base);
  const privateId = '66666666-6666-4666-8666-666666666605';
  assert.equal((await call(base, '/api/v1/public/media/' + privateId)).status, 404);
  const privateBytes = await call(base, '/api/v1/admin/media/' + privateId + '/content', {}, editor);
  assert.equal(privateBytes.status, 200);
  assert.equal(privateBytes.headers.get('cache-control'), 'private, no-store');

  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/N9sAAAAASUVORK5CYII=', 'base64');
  const validForm = new FormData();
  validForm.append('file', new Blob([png], {type: 'image/png'}), 'nueva.png');
  validForm.append('visibility', 'PUBLIC');
  assert.equal((await call(base, '/api/v1/admin/media', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: validForm}, editor)).status, 201);

  const mismatch = new FormData();
  mismatch.append('file', new Blob([png], {type: 'image/png'}), 'imagen.svg');
  mismatch.append('visibility', 'PUBLIC');
  assert.equal((await call(base, '/api/v1/admin/media', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: mismatch}, editor)).status, 415);

  const tooLargePng = Buffer.concat([png, Buffer.alloc(5 * 1024 * 1024 + 1 - png.length)]);
  const oversized = new FormData();
  oversized.append('file', new Blob([tooLargePng], {type: 'image/png'}), 'grande.png');
  oversized.append('visibility', 'PRIVATE');
  const rejected = await call(base, '/api/v1/admin/media', {method: 'POST', headers: {'X-CSRF-TOKEN': editor.csrf}, body: oversized}, editor);
  assert.equal(rejected.status, 413);
  assert.equal((await rejected.json()).code, 'FILE_TOO_LARGE');
});
