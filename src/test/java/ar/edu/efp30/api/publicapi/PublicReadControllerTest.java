package ar.edu.efp30.api.publicapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "efp30.media.root=build/test-media")
class PublicReadControllerTest {

    private static final UUID NEWS_PUBLISHED = uuid("11111111-1111-4111-8111-111111111101");
    private static final UUID NEWS_SECOND = uuid("11111111-1111-4111-8111-111111111102");
    private static final UUID NEWS_DRAFT = uuid("11111111-1111-4111-8111-111111111103");
    private static final UUID FAMILY_ACTIVE = uuid("44444444-4444-4444-8444-444444444401");
    private static final UUID FAMILY_INACTIVE = uuid("44444444-4444-4444-8444-444444444402");
    private static final UUID COURSE_PUBLISHED = uuid("33333333-3333-4333-8333-333333333301");
    private static final UUID COURSE_DRAFT = uuid("33333333-3333-4333-8333-333333333302");
    private static final UUID EVENT_ALL_DAY = uuid("22222222-2222-4222-8222-222222222201");
    private static final UUID EVENT_TIMED = uuid("22222222-2222-4222-8222-222222222202");
    private static final UUID EVENT_DRAFT = uuid("22222222-2222-4222-8222-222222222203");
    private static final UUID MEDIA_COVER = uuid("66666666-6666-4666-8666-666666666601");
    private static final UUID MEDIA_GALLERY = uuid("66666666-6666-4666-8666-666666666602");
    private static final UUID MEDIA_PRIVATE = uuid("66666666-6666-4666-8666-666666666603");
    private static final UUID MEDIA_DISABLED = uuid("66666666-6666-4666-8666-666666666604");
    private static final UUID PHOTO_SELECTED = uuid("55555555-5555-4555-8555-555555555501");
    private static final UUID PHOTO_DISABLED = uuid("55555555-5555-4555-8555-555555555502");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedPublicAndPrivateContent() throws Exception {
        jdbc.update("DELETE FROM course_photo");
        jdbc.update("DELETE FROM course_requirement");
        jdbc.update("DELETE FROM course");
        jdbc.update("DELETE FROM calendar_event");
        jdbc.update("DELETE FROM news");
        jdbc.update("DELETE FROM institution");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM professional_family");

        Path mediaRoot = Path.of("build/test-media");
        Files.createDirectories(mediaRoot);
        Files.write(mediaRoot.resolve("cover.jpg"), new byte[]{1, 2, 3, 4});
        Files.write(mediaRoot.resolve("gallery.jpg"), new byte[]{5, 6, 7, 8});
        Files.write(mediaRoot.resolve("private.pdf"), new byte[]{9, 10, 11, 12});
        Files.write(mediaRoot.resolve("disabled.png"), new byte[]{13, 14, 15, 16});

        addMedia(MEDIA_COVER, "cover.jpg", "image/jpeg", "PUBLIC", true, 4);
        addMedia(MEDIA_GALLERY, "gallery.jpg", "image/jpeg", "PUBLIC", true, 4);
        addMedia(MEDIA_PRIVATE, "private.pdf", "application/pdf", "PRIVATE", true, 4);
        addMedia(MEDIA_DISABLED, "disabled.png", "image/png", "PUBLIC", false, 4);

        jdbc.update("INSERT INTO professional_family (id, slug, name, active, sort_order) VALUES (?, ?, ?, ?, ?)",
                FAMILY_ACTIVE, "energia", "Energía", true, 1);
        jdbc.update("INSERT INTO professional_family (id, slug, name, active, sort_order) VALUES (?, ?, ?, ?, ?)",
                FAMILY_INACTIVE, "automotores", "Automotores", false, 2);

        addNews(NEWS_PUBLISHED, "inscripciones-a-talleres", "Inscripciones a talleres",
                "Oferta de formación profesional.", "Institucional", true, "PUBLISHED", MEDIA_COVER,
                "2026-10-10T12:00:00-03:00");
        addNews(NEWS_SECOND, "jornada-institucional", "Jornada institucional",
                "Encuentro abierto.", "Institucional", false, "PUBLISHED", null,
                "2026-10-12T12:00:00-03:00");
        addNews(NEWS_DRAFT, "noticia-en-borrador", "Inscripciones secretas",
                "No debe ser pública.", "Institucional", true, "DRAFT", null,
                "2026-10-13T12:00:00-03:00");

        addCourse(COURSE_PUBLISHED, "electricidad-domiciliaria", "Electricidad domiciliaria",
                FAMILY_ACTIVE, MEDIA_COVER, "PUBLISHED");
        addCourse(COURSE_DRAFT, "panaderia", "Panadería", FAMILY_ACTIVE, null, "DRAFT");
        addCourse(uuid("33333333-3333-4333-8333-333333333303"), "mecanica", "Mecánica",
                FAMILY_INACTIVE, null, "PUBLISHED");
        jdbc.update("INSERT INTO course_requirement (course_id, position, requirement) VALUES (?, ?, ?)",
                COURSE_PUBLISHED, 0, "DNI");
        jdbc.update("INSERT INTO course_requirement (course_id, position, requirement) VALUES (?, ?, ?)",
                COURSE_PUBLISHED, 1, "Edad mínima 18 años");
        jdbc.update("INSERT INTO course_photo (id, course_id, media_id, alt, position, enabled, show_on_homepage) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                PHOTO_SELECTED, COURSE_PUBLISHED, MEDIA_GALLERY, "Tablero didáctico", 0, true, true);
        jdbc.update("INSERT INTO course_photo (id, course_id, media_id, alt, position, enabled, show_on_homepage) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                PHOTO_DISABLED, COURSE_PUBLISHED, MEDIA_DISABLED, "Foto deshabilitada", 1, false, true);

        addEvent(EVENT_ALL_DAY, "jornada-institucional", "Jornada institucional", "Encuentro abierto.",
                "2026-10-20T00:00:00-03:00", "2026-10-21T00:00:00-03:00", true, "PUBLISHED");
        addEvent(EVENT_TIMED, "charla-seguridad", "Charla de seguridad e higiene", "Charla abierta.",
                "2026-10-21T00:00:00-03:00", "2026-10-21T02:00:00-03:00", false, "PUBLISHED");
        addEvent(EVENT_DRAFT, "evento-privado", "Evento privado", "No debe aparecer.",
                "2026-10-20T10:00:00-03:00", "2026-10-20T11:00:00-03:00", false, "DRAFT");
    }

    @Test
    void newsFiltersPaginateAndNeverExposeDraftsOrEditorialFields() throws Exception {
        ResponseEntity<String> first = http.getForEntity("/api/v1/public/noticias?page=0&size=1", String.class);
        JsonNode firstJson = json(first);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstJson.path("items").size()).isEqualTo(1);
        assertThat(firstJson.path("items").get(0).path("slug").asText()).isEqualTo("jornada-institucional");
        assertThat(firstJson.path("totalItems").asLong()).isEqualTo(2);
        assertThat(firstJson.path("items").get(0).has("status")).isFalse();
        assertThat(firstJson.path("items").get(0).has("version")).isFalse();

        JsonNode secondPage = json(http.getForEntity("/api/v1/public/noticias?page=1&size=1", String.class));
        assertThat(secondPage.path("items").get(0).path("slug").asText()).isEqualTo("inscripciones-a-talleres");

        JsonNode filtered = json(http.getForEntity(
                "/api/v1/public/noticias?q=INSCRIPCIONES&category=Institucional&featured=true", String.class));
        assertThat(filtered.path("items").size()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("id").asText()).isEqualTo(NEWS_PUBLISHED.toString());

        ResponseEntity<String> draft = http.getForEntity("/api/v1/public/noticias/noticia-en-borrador", String.class);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void coursesRequirePublishedStatusAndActiveFamiliesAndKeepCoverSeparateFromGallery() throws Exception {
        JsonNode coursePage = json(http.getForEntity("/api/v1/public/cursos?q=domiciliaria", String.class));
        assertThat(coursePage.path("items").size()).isEqualTo(1);
        assertThat(coursePage.path("items").get(0).path("modality").asText()).isEqualTo("PRESENTIAL");
        assertThat(coursePage.path("items").get(0).path("family").path("slug").asText()).isEqualTo("energia");

        JsonNode families = json(http.getForEntity("/api/v1/public/familias", String.class));
        assertThat(families.path("items").size()).isEqualTo(1);
        assertThat(families.path("items").get(0).path("slug").asText()).isEqualTo("energia");

        JsonNode course = json(http.getForEntity(
                "/api/v1/public/cursos/electricidad-domiciliaria", String.class));
        assertThat(course.path("requirements").size()).isEqualTo(2);
        assertThat(course.path("photos").size()).isEqualTo(1);
        assertThat(course.path("coverUrl").asText()).endsWith(MEDIA_COVER.toString());
        assertThat(course.path("photos").get(0).path("imageUrl").asText()).endsWith(MEDIA_GALLERY.toString());
        assertThat(course.path("photos").get(0).path("imageUrl").asText()).isNotEqualTo(course.path("coverUrl").asText());
        assertThat(course.has("status")).isFalse();

        assertThat(http.getForEntity("/api/v1/public/cursos/panaderia", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/v1/public/cursos/mecanica", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void calendarUsesHalfOpenIntervalsAndPreservesArgentineOffsets() throws Exception {
        JsonNode firstDay = json(http.getForEntity(
                "/api/v1/public/eventos?from=2026-10-20T00:00:00-03:00&to=2026-10-21T00:00:00-03:00",
                String.class));
        assertThat(firstDay.path("items").size()).isEqualTo(1);
        assertThat(firstDay.path("items").get(0).path("id").asText()).isEqualTo(EVENT_ALL_DAY.toString());
        assertThat(firstDay.path("items").get(0).path("allDay").asBoolean()).isTrue();
        assertThat(firstDay.path("items").get(0).path("startsAt").asText()).endsWith("-03:00");

        JsonNode secondDay = json(http.getForEntity(
                "/api/v1/public/eventos?from=2026-10-21T00:00:00-03:00&to=2026-10-22T00:00:00-03:00",
                String.class));
        assertThat(secondDay.path("items").size()).isEqualTo(1);
        assertThat(secondDay.path("items").get(0).path("id").asText()).isEqualTo(EVENT_TIMED.toString());

        JsonNode invalid = json(http.getForEntity(
                "/api/v1/public/eventos?from=2026-10-21T00:00:00-03:00&to=2026-10-21T00:00:00-03:00",
                String.class));
        assertThat(invalid.path("status").asInt()).isEqualTo(400);
        assertThat(invalid.path("fieldErrors").get(0).path("field").asText()).isEqualTo("to");
    }

    @Test
    void carouselAndPublicMediaFilterByCoursePhotoAndMediaState() throws Exception {
        JsonNode carousel = json(http.getForEntity("/api/v1/public/portada/carrusel", String.class));
        assertThat(carousel.path("items").size()).isEqualTo(1);
        assertThat(carousel.path("items").get(0).path("mediaId").asText()).isEqualTo(MEDIA_GALLERY.toString());

        ResponseEntity<byte[]> image = http.getForEntity(
                "/api/v1/public/media/" + MEDIA_GALLERY, byte[].class);
        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(image.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getHeaders().getFirst("Content-Disposition")).contains("inline");
        assertThat(image.getBody()).containsExactly((byte) 5, (byte) 6, (byte) 7, (byte) 8);

        JsonNode privateMedia = json(http.getForEntity(
                "/api/v1/public/media/" + MEDIA_PRIVATE, String.class));
        JsonNode disabledMedia = json(http.getForEntity(
                "/api/v1/public/media/" + MEDIA_DISABLED, String.class));
        assertThat(privateMedia.path("status").asInt()).isEqualTo(404);
        assertThat(privateMedia.path("code").asText()).isEqualTo(disabledMedia.path("code").asText());
        assertThat(privateMedia.path("detail").asText()).isEqualTo(disabledMedia.path("detail").asText());

        jdbc.update("UPDATE course_photo SET enabled = FALSE WHERE id = ?", PHOTO_SELECTED);
        assertThat(json(http.getForEntity("/api/v1/public/portada/carrusel", String.class))
                .path("items").size()).isZero();
        jdbc.update("UPDATE course_photo SET enabled = TRUE WHERE id = ?", PHOTO_SELECTED);
        jdbc.update("UPDATE course SET status = 'ARCHIVED' WHERE id = ?", COURSE_PUBLISHED);
        assertThat(json(http.getForEntity("/api/v1/public/portada/carrusel", String.class))
                .path("items").size()).isZero();
        assertThat(http.getForEntity("/api/v1/public/cursos/electricidad-domiciliaria", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void validatesPaginationAndReportsInstitutionConfigurationWithoutLeakingInternals() throws Exception {
        ResponseEntity<String> invalidPage = http.getForEntity("/api/v1/public/noticias?size=101", String.class);
        JsonNode invalidPageJson = json(invalidPage);
        assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidPage.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(invalidPageJson.path("fieldErrors").get(0).path("field").asText()).isEqualTo("size");

        ResponseEntity<String> institution = http.getForEntity("/api/v1/public/institucion", String.class);
        JsonNode institutionProblem = json(institution);
        assertThat(institution.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(institutionProblem.path("code").asText()).isEqualTo("INSTITUTION_NOT_CONFIGURED");
        assertThat(institutionProblem.toString()).doesNotContain("password", "stacktrace", "spring_session");
    }

    private void addMedia(UUID id, String objectKey, String mimeType, String visibility, boolean enabled, long size) {
        jdbc.update("INSERT INTO media (id, object_key, original_name, mime_type, size_bytes, sha256, visibility, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, objectKey, objectKey, mimeType, size, "0".repeat(64), visibility, enabled);
    }

    private void addNews(UUID id, String slug, String title, String summary, String category, boolean featured,
                         String status, UUID cover, String publishedAt) {
        jdbc.update("INSERT INTO news (id, slug, title, summary, body_markdown, category, featured, cover_media_id, "
                        + "status, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, slug, title, summary, "Contenido de prueba.", category, featured, cover, status,
                OffsetDateTime.parse(publishedAt));
    }

    private void addCourse(UUID id, String slug, String title, UUID familyId, UUID cover, String status) {
        jdbc.update("INSERT INTO course (id, slug, title, family_id, summary, body_markdown, duration_hours, "
                        + "modality, enrollment_status, cover_media_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, slug, title, familyId, "Resumen de " + title + ".", "Descripción de " + title + ".",
                120, "PRESENTIAL", "CONSULTATION", cover, status);
    }

    private void addEvent(UUID id, String slug, String title, String description, String startsAt, String endsAt,
                          boolean allDay, String status) {
        jdbc.update("INSERT INTO calendar_event (id, slug, title, description, starts_at, ends_at, timezone, "
                        + "location, all_day, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, slug, title, description, OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt),
                "America/Argentina/Buenos_Aires", "Sede", allDay, status);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
