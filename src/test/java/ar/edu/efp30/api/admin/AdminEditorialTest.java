package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.auth.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEditorialTest {
    private static final UUID ADMIN_ID=UUID.fromString("77777777-7777-4777-8777-777777777799");
    private static final UUID FAMILY_ID=UUID.fromString("44444444-4444-4444-8444-444444444499");
    private static final UUID MEDIA_ID=UUID.fromString("66666666-6666-4666-8666-666666666699");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void seed() {
        jdbc.update("DELETE FROM course_photo"); jdbc.update("DELETE FROM course_requirement");
        jdbc.update("DELETE FROM course"); jdbc.update("DELETE FROM calendar_event"); jdbc.update("DELETE FROM news");
        jdbc.update("DELETE FROM institution"); jdbc.update("DELETE FROM media"); jdbc.update("DELETE FROM professional_family");
        jdbc.update("DELETE FROM admin_user_role WHERE user_id=?",ADMIN_ID); jdbc.update("DELETE FROM admin_user WHERE id=?",ADMIN_ID);
        jdbc.update("INSERT INTO admin_user (id,display_name,email,password_hash,enabled) VALUES (?,?,?,?,TRUE)",ADMIN_ID,"Administrador","admin-editorial@efp30.test","hash");
        jdbc.update("INSERT INTO admin_user_role (user_id,role) VALUES (?,'ADMIN')",ADMIN_ID);
        jdbc.update("INSERT INTO professional_family (id,slug,name,active,version) VALUES (?,?,?,TRUE,0)",FAMILY_ID,"oficios","Oficios");
        jdbc.update("INSERT INTO media (id,object_key,original_name,mime_type,size_bytes,sha256,visibility,enabled) VALUES (?,?,?,?,?,?,?,TRUE)",
                MEDIA_ID,"cover.jpg","cover.jpg","image/jpeg",4,"0".repeat(64),"PUBLIC");
    }

    @Test void newsUsesCsrfTransitionsAndOptimisticVersions() throws Exception {
        String create="""
                {"slug":"jornada-anual","title":"Jornada anual","summary":"Resumen","bodyMarkdown":"Contenido markdown.","coverMediaId":null,"category":"Institucional","featured":false}
                """;
        mvc.perform(post("/api/v1/admin/noticias").with(admin()).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden());
        JsonNode draft=json(mvc.perform(post("/api/v1/admin/noticias").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id=UUID.fromString(draft.path("id").asText());
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        mvc.perform(get("/api/v1/public/noticias/jornada-anual")).andExpect(status().isNotFound());
        JsonNode published=json(mvc.perform(post("/api/v1/admin/noticias/"+id+"/publish").with(admin()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        mvc.perform(get("/api/v1/public/noticias/jornada-anual")).andExpect(status().isOk());
        String stale="{\"version\":0,\"title\":\"Vieja\",\"summary\":\"Resumen\",\"bodyMarkdown\":\"Texto\",\"coverMediaId\":null,\"category\":\"Institucional\",\"featured\":false}";
        mvc.perform(put("/api/v1/admin/noticias/"+id).with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isConflict()).andExpect(r->assertThat(json(r.getResponse().getContentAsString()).path("code").asText()).isEqualTo("VERSION_CONFLICT"));
        JsonNode archived=json(mvc.perform(post("/api/v1/admin/noticias/"+id+"/archive").with(admin()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        mvc.perform(get("/api/v1/public/noticias/jornada-anual")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/noticias/"+id+"/publish").with(admin()).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().isConflict());
    }

    @Test void galleryPublishingAndCalendarPreservePublicRulesAndArgentinaOffsets() throws Exception {
        String course="""
                {"slug":"soldadura","title":"Soldadura","familyId":"44444444-4444-4444-8444-444444444499","summary":"Introducción","bodyMarkdown":"Prácticas iniciales","requirements":["DNI"],"durationHours":80,"modality":"PRESENTIAL","enrollmentStatus":"CONSULTATION","coverMediaId":"66666666-6666-4666-8666-666666666699"}
                """;
        JsonNode created=json(mvc.perform(post("/api/v1/admin/cursos").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(course))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID courseId=UUID.fromString(created.path("id").asText());
        String photos="{\"version\":0,\"photos\":[{\"id\":null,\"mediaId\":\""+MEDIA_ID+"\",\"alt\":\"Taller\",\"position\":0,\"enabled\":true,\"showOnHomepage\":true}]}";
        JsonNode gallery=json(mvc.perform(put("/api/v1/admin/cursos/"+courseId+"/photos").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(photos))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(gallery.path("version").asLong()).isEqualTo(1);
        mvc.perform(post("/api/v1/admin/cursos/"+courseId+"/publish").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk());
        assertThat(json(mvc.perform(get("/api/v1/public/portada/carrusel")).andReturn().getResponse().getContentAsString()).path("items").size()).isEqualTo(1);
        String event="""
                {"slug":"feria-oficios","title":"Feria de oficios","description":"Encuentro abierto","startsAt":"2026-10-20T00:00:00-03:00","endsAt":"2026-10-21T00:00:00-03:00","timezone":"America/Argentina/Buenos_Aires","location":"Sede","allDay":true}
                """;
        JsonNode eventDraft=json(mvc.perform(post("/api/v1/admin/eventos").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID eventId=UUID.fromString(eventDraft.path("id").asText());
        mvc.perform(post("/api/v1/admin/eventos/"+eventId+"/publish").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        JsonNode day=json(mvc.perform(get("/api/v1/public/eventos?from=2026-10-20T00:00:00-03:00&to=2026-10-21T00:00:00-03:00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(day.path("items").size()).isEqualTo(1);
        assertThat(day.path("items").get(0).path("startsAt").asText()).endsWith("-03:00");
        mvc.perform(post("/api/v1/admin/eventos").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(event.replace("2026-10-20T00:00:00-03:00","2026-10-20T10:00:00-03:00"))).andExpect(status().isBadRequest());
    }

    @Test void institutionRequiresAdminAndVersionZeroInitializesIt() throws Exception {
        mvc.perform(get("/api/v1/admin/institucion").with(editor())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/institucion").with(admin())).andExpect(status().isServiceUnavailable());
        String body="""
                {"version":0,"name":"Escuela de Formación Profesional N°30","description":"Formación profesional pública","address":"Av. Ejemplo 1234","email":"contacto@efp30.example","phones":["+54 11 4000-0000"],"socialLinks":[{"label":"Instagram","url":"https://example.com/efp30"}]}
                """;
        JsonNode saved=json(mvc.perform(put("/api/v1/admin/institucion").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.path("version").asLong()).isZero();
        mvc.perform(get("/api/v1/public/institucion")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/institucion").with(admin()).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"version\":0","\"version\":1"))).andExpect(status().isConflict());
    }

    private RequestPostProcessor admin() { return user(new AdminPrincipal(ADMIN_ID,"Administrador","admin-editorial@efp30.test","unused",List.of("ADMIN"),true)); }
    private RequestPostProcessor editor() { return user(new AdminPrincipal(ADMIN_ID,"Editor","editor@efp30.test","unused",List.of("EDITOR"),true)); }
    private JsonNode json(String body) throws Exception { return mapper.readTree(body); }
}

