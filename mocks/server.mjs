import {createServer} from 'node:http';
import {randomUUID} from 'node:crypto';
import {basename} from 'node:path';
import {pathToFileURL} from 'node:url';
import {readFile} from 'node:fs/promises';

const fixture = JSON.parse(await readFile(new URL('../fixtures/editorial.json', import.meta.url), 'utf8'));
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/N9sAAAAASUVORK5CYII=', 'base64');
const PDF = Buffer.from('%PDF-1.4\n% EFP30 local mock\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF');
const BASE = '/api/v1';
const EDITOR_ROLES = ['ADMIN', 'EDITOR'];
const DATA_KEY = {noticias: 'news', cursos: 'courses', familias: 'families', eventos: 'events', users: 'users', media: 'media'};
const clone = (value) => structuredClone(value);
const now = () => new Date().toISOString();

function cookieId(request) {
  return request.headers.cookie?.split(';').map((part) => part.trim()).find((part) => part.startsWith('EFP30_SESSION='))?.slice('EFP30_SESSION='.length);
}

function sendJson(response, status, value, headers = {}) {
  response.writeHead(status, {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers});
  response.end(JSON.stringify(value));
}

function sendProblem(response, status, code, detail, instance, fieldErrors = []) {
  const title = {400: 'Solicitud inválida', 401: 'No autenticado', 403: 'Acceso denegado', 404: 'No encontrado', 409: 'Conflicto', 413: 'Archivo grande', 415: 'Tipo no permitido', 429: 'Demasiados intentos', 500: 'Error interno'}[status];
  sendJson(response, status, {type: 'about:blank', title, status, detail, instance, code, fieldErrors}, {'Content-Type': 'application/problem+json; charset=utf-8'});
}

async function readBody(request, limit = 11 * 1024 * 1024) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of request) {
    bytes += chunk.length;
    if (bytes > limit) throw Object.assign(new Error('Se superó el límite de lectura.'), {status: 413, code: 'FILE_TOO_LARGE'});
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function readJson(request) {
  try {
    const raw = (await readBody(request, 1024 * 1024)).toString('utf8');
    return raw ? JSON.parse(raw) : {};
  } catch (error) {
    if (error.status) throw error;
    throw Object.assign(new Error('El cuerpo JSON no es válido.'), {status: 400, code: 'INVALID_JSON'});
  }
}

function paginated(items, url) {
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  if (!Number.isInteger(page) || page < 0) throw Object.assign(new Error('page debe ser entero desde 0.'), {status: 400, code: 'INVALID_PAGE'});
  if (!Number.isInteger(size) || size < 1 || size > 100) throw Object.assign(new Error('size debe estar entre 1 y 100.'), {status: 400, code: 'INVALID_SIZE'});
  return {items: items.slice(page * size, page * size + size), page, size, totalItems: items.length, totalPages: Math.ceil(items.length / size)};
}

function checkVersion(record, body) {
  if (!Number.isInteger(body.version) || body.version !== record.version) throw Object.assign(new Error('El recurso cambió; actualizalo antes de guardar.'), {status: 409, code: 'VERSION_CONFLICT'});
}

function isImage(media) {
  return Boolean(media?.enabled && media.visibility === 'PUBLIC' && media.mimeType.startsWith('image/'));
}

function mediaUrl(media) {
  return media?.enabled && media.visibility === 'PUBLIC' ? BASE + '/public/media/' + media.id : null;
}

function publicNews(state, item) {
  const {id, slug, title, summary, bodyMarkdown, coverMediaId, category, featured, publishedAt} = item;
  return {id, slug, title, summary, bodyMarkdown, coverUrl: mediaUrl(state.media.find((m) => m.id === coverMediaId)), category, featured, publishedAt};
}

function publicCourse(state, item, detail = true) {
  const family = state.families.find((entry) => entry.id === item.familyId);
  const result = {
    id: item.id, slug: item.slug, title: item.title,
    family: {id: family.id, slug: family.slug, name: family.name},
    summary: item.summary, durationHours: item.durationHours,
    modality: item.modality, enrollmentStatus: item.enrollmentStatus,
    coverUrl: mediaUrl(state.media.find((m) => m.id === item.coverMediaId))
  };
  if (detail) {
    result.bodyMarkdown = item.bodyMarkdown;
    result.requirements = item.requirements;
    result.photos = (state.coursePhotos[item.id] ?? [])
      .filter((photo) => photo.enabled && isImage(state.media.find((m) => m.id === photo.mediaId)))
      .sort((a, b) => a.position - b.position || a.id.localeCompare(b.id))
      .map((photo) => ({id: photo.id, imageUrl: BASE + '/public/media/' + photo.mediaId, alt: photo.alt, position: photo.position}));
  }
  return result;
}

function publicEvent(item) {
  const {id, slug, title, description, startsAt, endsAt, timezone, location, allDay} = item;
  return {id, slug, title, description, startsAt, endsAt, timezone, location, allDay};
}

function adminMedia(item) {
  return {...item, url: mediaUrl(item)};
}

function publicCourses(state) {
  return state.courses.filter((course) => course.status === 'PUBLISHED' && state.families.some((family) => family.id === course.familyId && family.active));
}

function validateEvent(event) {
  const errors = [];
  const start = Date.parse(event.startsAt);
  const end = Date.parse(event.endsAt);
  if (!Number.isFinite(start)) errors.push({field: 'startsAt', message: 'Usá ISO 8601 con offset.'});
  if (!Number.isFinite(end) || end <= start) errors.push({field: 'endsAt', message: 'Debe ser posterior a startsAt.'});
  if (event.timezone !== 'America/Argentina/Buenos_Aires') errors.push({field: 'timezone', message: 'Usá America/Argentina/Buenos_Aires.'});
  const midnight = /^\d{4}-\d{2}-\d{2}T00:00:00[+-]\d{2}:\d{2}$/;
  if (event.allDay && (!midnight.test(event.startsAt) || !midnight.test(event.endsAt))) errors.push({field: 'allDay', message: 'Medianoche local y fin exclusivo.'});
  if (errors.length) throw Object.assign(new Error('Revisá fechas y zona horaria.'), {status: 400, code: 'VALIDATION_ERROR', fieldErrors: errors});
}

function hasPublicReference(state, mediaId) {
  return state.news.some((item) => item.status === 'PUBLISHED' && item.coverMediaId === mediaId)
    || state.courses.some((item) => item.status === 'PUBLISHED' && item.coverMediaId === mediaId)
    || Object.entries(state.coursePhotos).some(([courseId, photos]) => {
      const course = state.courses.find((item) => item.id === courseId);
      return course?.status === 'PUBLISHED' && photos.some((photo) => photo.enabled && photo.mediaId === mediaId);
    });
}

async function parseUpload(request) {
  const raw = await readBody(request);
  const form = await new Request('http://localhost/upload', {method: 'POST', headers: request.headers, body: raw}).formData();
  const file = form.get('file');
  const visibility = form.get('visibility');
  if (!(file instanceof File) || !['PUBLIC', 'PRIVATE'].includes(visibility)) throw Object.assign(new Error('file y visibility son obligatorios.'), {status: 400, code: 'VALIDATION_ERROR'});
  const bytes = Buffer.from(await file.arrayBuffer());
  let mimeType;
  if (bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) mimeType = 'image/png';
  else if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) mimeType = 'image/jpeg';
  else if (bytes.length >= 12 && bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP') mimeType = 'image/webp';
  else if (bytes.toString('ascii', 0, 5) === '%PDF-') mimeType = 'application/pdf';
  else throw Object.assign(new Error('Los bytes no corresponden a un tipo admitido.'), {status: 415, code: 'MEDIA_TYPE_UNSUPPORTED'});
  const max = mimeType === 'application/pdf' ? 10 * 1024 * 1024 : 5 * 1024 * 1024;
  if (!bytes.length || bytes.length > max) throw Object.assign(new Error('Se superó el límite por tipo.'), {status: 413, code: 'FILE_TOO_LARGE'});
  const name = basename(file.name.replaceAll('\\', '/')).replace(/[^\p{L}\p{N}._-]/gu, '-').slice(0, 180) || 'archivo';
  const extension = name.toLowerCase().split('.').pop();
  const extensions = {png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', pdf: 'application/pdf'};
  if (extensions[extension] !== mimeType) throw Object.assign(new Error('La extensión no coincide con los bytes.'), {status: 415, code: 'MEDIA_TYPE_UNSUPPORTED'});
  return {bytes, name, mimeType, visibility};
}

export function createMockServer() {
  const state = clone(fixture);
  const sessions = new Map();
  const fileBytes = new Map(state.media.map((item) => [item.id, item.mimeType === 'application/pdf' ? PDF : PNG]));
  const addSession = (user = null) => {
    const id = randomUUID();
    const session = {user, csrf: randomUUID()};
    sessions.set(id, session);
    return {id, session};
  };

  const server = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://localhost');
    const pathname = url.pathname;
    try {
      if (request.method === 'GET' && pathname === '/health') return sendJson(response, 200, {status: 'UP'});
      if (!pathname.startsWith(BASE + '/')) return sendProblem(response, 404, 'NOT_FOUND', 'La ruta no existe.', pathname);
      const existingId = cookieId(request);
      const current = sessions.get(existingId);

      if (request.method === 'GET' && pathname === BASE + '/auth/csrf') {
        if (current) return sendJson(response, 200, {token: current.csrf, headerName: 'X-CSRF-TOKEN'});
        const {id, session} = addSession();
        return sendJson(response, 200, {token: session.csrf, headerName: 'X-CSRF-TOKEN'}, {'Set-Cookie': 'EFP30_SESSION=' + id + '; Path=/; HttpOnly; SameSite=Lax'});
      }
      if (request.method === 'POST' && pathname === BASE + '/auth/login') {
        if (!current || request.headers['x-csrf-token'] !== current.csrf) return sendProblem(response, 403, 'CSRF_INVALID', 'Volvé a pedir el token CSRF.', pathname);
        const body = await readJson(request);
        const user = state.users.find((item) => item.email.toLowerCase() === String(body.email).toLowerCase() && item.enabled);
        if (!user || typeof body.password !== 'string' || !body.password) return sendProblem(response, 401, 'UNAUTHENTICATED', 'Las credenciales no son válidas.', pathname);
        sessions.delete(existingId);
        const {id} = addSession(user);
        return sendJson(response, 200, {user: {id: user.id, displayName: user.displayName, email: user.email, roles: user.roles}}, {'Set-Cookie': 'EFP30_SESSION=' + id + '; Path=/; HttpOnly; SameSite=Lax'});
      }
      if (request.method === 'GET' && pathname === BASE + '/auth/me') {
        if (!current?.user?.enabled) return sendProblem(response, 401, 'UNAUTHENTICATED', 'La sesión no es válida.', pathname);
        return sendJson(response, 200, {user: {id: current.user.id, displayName: current.user.displayName, email: current.user.email, roles: current.user.roles}});
      }
      if (request.method === 'POST' && pathname === BASE + '/auth/logout') {
        if (!current?.user) return sendProblem(response, 401, 'UNAUTHENTICATED', 'La sesión no es válida.', pathname);
        if (request.headers['x-csrf-token'] !== current.csrf) return sendProblem(response, 403, 'CSRF_INVALID', 'Volvé a pedir el token CSRF.', pathname);
        sessions.delete(existingId);
        response.writeHead(204, {'Set-Cookie': 'EFP30_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0', 'Cache-Control': 'no-store'});
        return response.end();
      }
      if (request.method === 'POST' && pathname === BASE + '/auth/password') {
        if (!current?.user) return sendProblem(response, 401, 'UNAUTHENTICATED', 'La sesión no es válida.', pathname);
        if (request.headers['x-csrf-token'] !== current.csrf) return sendProblem(response, 403, 'CSRF_INVALID', 'Volvé a pedir el token CSRF.', pathname);
        const body = await readJson(request);
        if (typeof body.newPassword !== 'string' || body.newPassword.length < 12) return sendProblem(response, 400, 'VALIDATION_ERROR', 'La contraseña debe tener 12 caracteres.', pathname, [{field: 'newPassword', message: 'Mínimo 12 caracteres.'}]);
        response.writeHead(204, {'Cache-Control': 'no-store'});
        return response.end();
      }

      if (pathname.startsWith(BASE + '/public/')) {
        if (request.method !== 'GET') return sendProblem(response, 404, 'NOT_FOUND', 'La ruta no existe.', pathname);
        const parts = pathname.slice((BASE + '/public/').length).split('/');
        if (parts[0] === 'portada' && parts[1] === 'carrusel') {
          const items = [];
          for (const course of publicCourses(state)) for (const photo of state.coursePhotos[course.id] ?? []) {
            const media = state.media.find((item) => item.id === photo.mediaId);
            if (photo.enabled && photo.showOnHomepage && isImage(media)) items.push({id: photo.id, courseId: course.id, courseSlug: course.slug, courseTitle: course.title, mediaId: media.id, imageUrl: BASE + '/public/media/' + media.id, alt: photo.alt, position: photo.position});
          }
          items.sort((a, b) => a.position - b.position || a.courseId.localeCompare(b.courseId) || a.id.localeCompare(b.id));
          return sendJson(response, 200, {items});
        }
        if (parts[0] === 'noticias' && parts.length === 1) {
          let items = state.news.filter((item) => item.status === 'PUBLISHED');
          const q = url.searchParams.get('q')?.toLocaleLowerCase('es');
          const category = url.searchParams.get('category');
          const featured = url.searchParams.get('featured');
          if (q) items = items.filter((item) => (item.title + ' ' + item.summary).toLocaleLowerCase('es').includes(q));
          if (category) items = items.filter((item) => item.category === category);
          if (featured !== null) items = items.filter((item) => String(item.featured) === featured);
          items.sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || a.id.localeCompare(b.id));
          return sendJson(response, 200, paginated(items.map((item) => publicNews(state, item)), url));
        }
        if (parts[0] === 'noticias' && parts.length === 2) {
          const item = state.news.find((entry) => entry.slug === parts[1] && entry.status === 'PUBLISHED');
          return item ? sendJson(response, 200, publicNews(state, item)) : sendProblem(response, 404, 'NOT_FOUND', 'No existe o no está disponible.', pathname);
        }
        if (parts[0] === 'cursos' && parts.length === 1) {
          let items = publicCourses(state);
          const q = url.searchParams.get('q')?.toLocaleLowerCase('es');
          const familyId = url.searchParams.get('familyId');
          if (q) items = items.filter((item) => (item.title + ' ' + item.summary).toLocaleLowerCase('es').includes(q));
          if (familyId) items = items.filter((item) => item.familyId === familyId);
          items.sort((a, b) => a.title.localeCompare(b.title, 'es') || a.id.localeCompare(b.id));
          return sendJson(response, 200, paginated(items.map((item) => publicCourse(state, item, false)), url));
        }
        if (parts[0] === 'cursos' && parts.length === 2) {
          const item = publicCourses(state).find((entry) => entry.slug === parts[1]);
          return item ? sendJson(response, 200, publicCourse(state, item)) : sendProblem(response, 404, 'NOT_FOUND', 'No existe o no está disponible.', pathname);
        }
        if (parts[0] === 'familias' && parts.length === 1) {
          const visible = new Set(publicCourses(state).map((course) => course.familyId));
          return sendJson(response, 200, {items: state.families.filter((family) => family.active && visible.has(family.id)).map(({id, slug, name}) => ({id, slug, name}))});
        }
        if (parts[0] === 'institucion' && parts.length === 1) {
          const {name, description, address, email, phones, socialLinks} = state.institution;
          return sendJson(response, 200, {name, description, address, email, phones, socialLinks});
        }
        if (parts[0] === 'eventos' && parts.length === 1) {
          const from = Date.parse(url.searchParams.get('from') ?? '');
          const to = Date.parse(url.searchParams.get('to') ?? '');
          if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) return sendProblem(response, 400, 'VALIDATION_ERROR', 'from/to deben ser ISO 8601 y to posterior a from.', pathname, [{field: 'from/to', message: 'Intervalo [from,to) inválido.'}]);
          const items = state.events.filter((event) => event.status === 'PUBLISHED' && Date.parse(event.startsAt) < to && Date.parse(event.endsAt) > from)
            .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt) || a.id.localeCompare(b.id)).map(publicEvent);
          return sendJson(response, 200, paginated(items, url));
        }
        if (parts[0] === 'eventos' && parts.length === 2) {
          const item = state.events.find((entry) => entry.slug === parts[1] && entry.status === 'PUBLISHED');
          return item ? sendJson(response, 200, publicEvent(item)) : sendProblem(response, 404, 'NOT_FOUND', 'No existe o no está disponible.', pathname);
        }
        if (parts[0] === 'media' && parts.length === 2) {
          const media = state.media.find((item) => item.id === parts[1]);
          if (!media?.enabled || media.visibility !== 'PUBLIC') return sendProblem(response, 404, 'NOT_FOUND', 'No existe o no está disponible.', pathname);
          response.writeHead(200, {'Content-Type': media.mimeType, 'Content-Disposition': media.mimeType === 'application/pdf' ? 'attachment; filename=\"' + media.originalName + '\"' : 'inline', 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'public, max-age=60'});
          return response.end(fileBytes.get(media.id) ?? PNG);
        }
      }

      if (pathname === BASE + '/admin/institucion') {
        if (!current?.user?.enabled) return sendProblem(response, 401, 'UNAUTHENTICATED', 'La sesión no es válida.', pathname);
        if (!current.user.roles.includes('ADMIN')) return sendProblem(response, 403, 'FORBIDDEN', 'Sólo ADMIN puede gestionar institución.', pathname);
        if (request.method === 'GET') return sendJson(response, 200, state.institution);
        if (request.method === 'PUT') {
          if (request.headers['x-csrf-token'] !== current.csrf) return sendProblem(response, 403, 'CSRF_INVALID', 'Volvé a pedir el token CSRF.', pathname);
          const body = await readJson(request);
          checkVersion(state.institution, body);
          state.institution = {...state.institution, ...body, version: state.institution.version + 1};
          return sendJson(response, 200, state.institution);
        }
      }
      const match = pathname.match(/^\/api\/v1\/admin\/(noticias|cursos|familias|eventos|users|media)(?:\/([^/]+)(?:\/(photos|publish|archive|password|content))?)?$/);
      if (match) {
        const [, resource, id, action] = match;
        if (!current?.user?.enabled) return sendProblem(response, 401, 'UNAUTHENTICATED', 'La sesión no es válida.', pathname);
        const allowed = resource === 'users' ? ['ADMIN'] : EDITOR_ROLES;
        if (!current.user.roles.some((role) => allowed.includes(role))) return sendProblem(response, 403, 'FORBIDDEN', 'No tenés permiso para esta operación.', pathname);
        if (!['GET', 'HEAD'].includes(request.method) && request.headers['x-csrf-token'] !== current.csrf) return sendProblem(response, 403, 'CSRF_INVALID', 'Volvé a pedir el token CSRF.', pathname);
        return await handleAdmin({request, response, url, pathname, resource, id, action, state, fileBytes, sessions, current});
      }
      return sendProblem(response, 404, 'NOT_FOUND', 'La ruta no existe.', pathname);
    } catch (error) {
      sendProblem(response, error.status ?? 500, error.code ?? 'INTERNAL_ERROR', error.status ? error.message : 'No se pudo completar la solicitud.', pathname, error.fieldErrors ?? []);
    }
  });
  return server;
}

async function handleAdmin({request, response, url, pathname, resource, id, action, state, fileBytes, sessions, current}) {
  const collection = state[DATA_KEY[resource]];
  if (!id) {
    if (request.method === 'GET') {
      let items = [...collection];
      const status = url.searchParams.get('status');
      const q = url.searchParams.get('q')?.toLocaleLowerCase('es');
      const familyId = url.searchParams.get('familyId');
      const active = url.searchParams.get('active');
      const enabled = url.searchParams.get('enabled');
      const role = url.searchParams.get('role');
      const visibility = url.searchParams.get('visibility');
      const category = url.searchParams.get('category');
      if (status) items = items.filter((item) => item.status === status);
      if (q) items = items.filter((item) => ((item.title ?? '') + ' ' + (item.summary ?? '') + ' ' + (item.name ?? '')).toLocaleLowerCase('es').includes(q));
      if (familyId) items = items.filter((item) => item.familyId === familyId);
      if (active !== null) items = items.filter((item) => String(item.active) === active);
      if (enabled !== null) items = items.filter((item) => String(item.enabled) === enabled);
      if (role) items = items.filter((item) => item.roles?.includes(role));
      if (visibility) items = items.filter((item) => item.visibility === visibility);
      if (category) items = items.filter((item) => item.category === category);
      if (resource === 'cursos') items.sort((a, b) => a.title.localeCompare(b.title, 'es') || a.id.localeCompare(b.id));
      if (resource === 'eventos') items.sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt) || a.id.localeCompare(b.id));
      if (resource === 'users') items.sort((a, b) => a.email.localeCompare(b.email));
      return sendJson(response, 200, paginated(items.map((item) => resource === 'media' ? adminMedia(item) : item), url));
    }
    if (request.method !== 'POST') return sendProblem(response, 405, 'METHOD_NOT_ALLOWED', 'Método no admitido.', pathname);
    if (resource === 'media') {
      const upload = await parseUpload(request);
      const media = {id: randomUUID(), originalName: upload.name, mimeType: upload.mimeType, sizeBytes: upload.bytes.length, visibility: upload.visibility, enabled: true, version: 0};
      state.media.push(media);
      fileBytes.set(media.id, upload.bytes);
      return sendJson(response, 201, adminMedia(media), {Location: BASE + '/admin/media/' + media.id});
    }
    const body = await readJson(request);
    if ((resource === 'noticias' || resource === 'cursos' || resource === 'eventos' || resource === 'familias') && collection.some((item) => item.slug === body.slug)) return sendProblem(response, 409, 'SLUG_EXISTS', 'El slug ya existe.', pathname);
    if (resource === 'users' && collection.some((item) => item.email.toLowerCase() === String(body.email).toLowerCase())) return sendProblem(response, 409, 'EMAIL_EXISTS', 'El email ya existe.', pathname);
    const created = now();
    let item;
    if (resource === 'noticias') item = {...body, id: randomUUID(), status: 'DRAFT', publishedAt: null, createdAt: created, updatedAt: created, version: 0};
    if (resource === 'cursos') {
      if (!state.families.some((family) => family.id === body.familyId)) return sendProblem(response, 400, 'INVALID_FAMILY', 'La familia no existe.', pathname);
      item = {...body, id: randomUUID(), status: 'DRAFT', publishedAt: null, createdAt: created, updatedAt: created, version: 0};
      state.coursePhotos[item.id] = [];
    }
    if (resource === 'eventos') {
      validateEvent(body);
      item = {...body, id: randomUUID(), status: 'DRAFT', publishedAt: null, createdAt: created, updatedAt: created, version: 0};
    }
    if (resource === 'familias') item = {...body, id: randomUUID(), active: true, version: 0};
    if (resource === 'users') {
      if (!Array.isArray(body.roles) || !body.roles.length || body.roles.some((role) => !['ADMIN', 'EDITOR'].includes(role))) return sendProblem(response, 400, 'INVALID_ROLE', 'Sólo se admiten ADMIN o EDITOR.', pathname);
      item = {id: randomUUID(), displayName: body.displayName, email: String(body.email).toLowerCase(), roles: body.roles, enabled: true, createdAt: created, version: 0};
    }
    collection.push(item);
    return sendJson(response, 201, item, {Location: BASE + '/admin/' + resource + '/' + item.id});
  }

  const item = collection.find((entry) => entry.id === id);
  if (!item) return sendProblem(response, 404, 'NOT_FOUND', 'El recurso no existe.', pathname);
  if (resource === 'media' && action === 'content' && request.method === 'GET') {
    if (item.visibility !== 'PRIVATE' || !item.enabled) return sendProblem(response, 404, 'NOT_FOUND', 'No existe o no está disponible.', pathname);
    response.writeHead(200, {'Content-Type': item.mimeType, 'Content-Disposition': 'attachment; filename=\"' + item.originalName + '\"', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff'});
    return response.end(fileBytes.get(item.id) ?? PDF);
  }
  if (resource === 'media' && !action && request.method === 'GET') return sendJson(response, 200, adminMedia(item));
  if (resource === 'cursos' && action === 'photos') {
    const photos = state.coursePhotos[id] ?? [];
    if (request.method === 'GET') return sendJson(response, 200, {courseId: id, version: item.version, photos});
    if (request.method === 'PUT') {
      const body = await readJson(request);
      checkVersion(item, body);
      if (!Array.isArray(body.photos)) return sendProblem(response, 400, 'VALIDATION_ERROR', 'photos debe ser una lista.', pathname);
      for (const photo of body.photos) {
        if (!isImage(state.media.find((media) => media.id === photo.mediaId)) || !photo.alt?.trim()) return sendProblem(response, 400, 'INVALID_PHOTO', 'Revisá imagen pública y texto alternativo.', pathname);
        if (photo.id && !photos.some((entry) => entry.id === photo.id)) return sendProblem(response, 409, 'PHOTO_NOT_IN_COURSE', 'La asociación no pertenece a este curso.', pathname);
      }
      const next = body.photos.map((photo) => ({...photo, id: photo.id ?? randomUUID()}));
      state.coursePhotos[id] = next;
      item.version += 1;
      item.updatedAt = now();
      return sendJson(response, 200, {courseId: id, version: item.version, photos: next});
    }
  }
  if (resource === 'users' && action === 'password' && request.method === 'POST') {
    const body = await readJson(request);
    checkVersion(item, body);
    if (typeof body.newPassword !== 'string' || body.newPassword.length < 12) return sendProblem(response, 400, 'VALIDATION_ERROR', 'La contraseña requiere 12 caracteres.', pathname);
    item.version += 1;
    response.writeHead(204, {'Cache-Control': 'no-store'});
    return response.end();
  }
  if (action === 'publish' || action === 'archive') {
    const body = await readJson(request);
    checkVersion(item, body);
    if ((action === 'publish' && item.status !== 'DRAFT') || (action === 'archive' && item.status === 'ARCHIVED')) {
      return sendProblem(response, 409, 'INVALID_STATE_TRANSITION', 'El estado actual no permite esta transición.', pathname);
    }
    if (action === 'publish') {
      if (resource === 'cursos' && !state.families.some((family) => family.id === item.familyId && family.active)) return sendProblem(response, 400, 'INVALID_FAMILY', 'La familia debe estar activa.', pathname);
      if (resource === 'eventos') validateEvent(item);
      item.status = 'PUBLISHED';
      item.publishedAt ??= now();
    } else item.status = 'ARCHIVED';
    item.version += 1;
    item.updatedAt = now();
    return sendJson(response, 200, resource === 'media' ? adminMedia(item) : item);
  }
  if (!action && request.method === 'GET') return sendJson(response, 200, resource === 'media' ? adminMedia(item) : item);
  if (!action && request.method === 'PUT') {
    const body = await readJson(request);
    checkVersion(item, body);
    if (resource === 'media') {
      if (body.visibility === 'PRIVATE' && hasPublicReference(state, item.id)) return sendProblem(response, 409, 'MEDIA_IN_USE_PUBLIC', 'Retirá referencias públicas antes de cambiar visibilidad.', pathname);
      item.enabled = body.enabled;
      item.visibility = body.visibility;
    } else if (resource === 'familias') {
      if (!body.active && state.courses.some((course) => course.familyId === id && course.status === 'PUBLISHED')) return sendProblem(response, 409, 'FAMILY_IN_USE', 'La familia tiene cursos publicados.', pathname);
      item.name = body.name;
      item.active = body.active;
    } else if (resource === 'users') {
      if (!Array.isArray(body.roles) || !body.roles.length || body.roles.some((role) => !['ADMIN', 'EDITOR'].includes(role))) return sendProblem(response, 400, 'INVALID_ROLE', 'Sólo se admiten ADMIN o EDITOR.', pathname);
      const lastAdmin = item.enabled && item.roles.includes('ADMIN') && (!body.enabled || !body.roles.includes('ADMIN')) && state.users.filter((user) => user.enabled && user.roles.includes('ADMIN')).length <= 1;
      if (lastAdmin) return sendProblem(response, 409, 'LAST_ADMIN', 'Debe quedar un ADMIN habilitado.', pathname);
      Object.assign(item, {displayName: body.displayName, roles: body.roles, enabled: body.enabled});
      for (const session of sessions.values()) if (session.user?.id === item.id) session.user = {...item};
    } else Object.assign(item, body);
    item.version += 1;
    if (item.updatedAt) item.updatedAt = now();
    return sendJson(response, 200, resource === 'media' ? adminMedia(item) : item);
  }
  return sendProblem(response, 405, 'METHOD_NOT_ALLOWED', 'Método no admitido.', pathname);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.MOCK_PORT ?? 4010);
  createMockServer().listen(port, '127.0.0.1', () => console.log('EFP30 API mock en http://127.0.0.1:' + port + BASE));
}
