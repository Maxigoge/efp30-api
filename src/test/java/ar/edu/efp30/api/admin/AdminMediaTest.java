package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.auth.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties="efp30.media.root=build/admin-media-test")
class AdminMediaTest {
    private static final UUID ADMIN_ID=UUID.fromString("77777777-7777-4777-8777-777777777788");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM course_photo");jdbc.update("DELETE FROM course_requirement");jdbc.update("DELETE FROM course");
        jdbc.update("DELETE FROM news");jdbc.update("DELETE FROM calendar_event");jdbc.update("DELETE FROM institution");
        jdbc.update("DELETE FROM media");jdbc.update("DELETE FROM professional_family");
        jdbc.update("DELETE FROM admin_user_role WHERE user_id=?",ADMIN_ID);jdbc.update("DELETE FROM admin_user WHERE id=?",ADMIN_ID);
        jdbc.update("INSERT INTO admin_user (id,display_name,email,password_hash,enabled) VALUES (?,?,?,?,TRUE)",ADMIN_ID,"Administrador","admin-media@efp30.test","hash");
        jdbc.update("INSERT INTO admin_user_role (user_id,role) VALUES (?,'ADMIN')",ADMIN_ID);
    }

    @Test void validatesBytesAndExtensionAndStoresPrivateFilesOutsideDatabase() throws Exception {
        MockMultipartFile privatePng=new MockMultipartFile("file","respaldo.png","image/png",pngBytes(32));
        mvc.perform(multipart("/api/v1/admin/media").file(privatePng).param("visibility","PRIVATE").with(admin()))
                .andExpect(status().isForbidden());
        MvcResult uploaded=mvc.perform(multipart("/api/v1/admin/media").file(privatePng).param("visibility","PRIVATE")
                        .with(admin()).with(csrf().asHeader()))
                .andExpect(status().isCreated()).andReturn();
        JsonNode media=json(uploaded.getResponse().getContentAsString());
        UUID id=UUID.fromString(media.path("id").asText());
        assertThat(media.path("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(media.path("url").isNull()).isTrue();
        assertThat(media.has("objectKey")).isFalse();
        MvcResult privateContent=mvc.perform(get("/api/v1/admin/media/"+id+"/content").with(editor()))
                .andExpect(status().isOk()).andReturn();
        assertThat(privateContent.getResponse().getContentAsByteArray()).containsExactly(pngBytes(32));
        assertThat(privateContent.getResponse().getHeader("Cache-Control")).contains("private","no-store");
        assertThat(privateContent.getResponse().getHeader("Content-Disposition")).contains("attachment");
        assertThat(privateContent.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        mvc.perform(get("/api/v1/public/media/"+id)).andExpect(status().isNotFound());

        String update="{\"version\":0,\"enabled\":true,\"visibility\":\"PUBLIC\"}";
        mvc.perform(put("/api/v1/admin/media/"+id).with(admin()).with(csrf().asHeader())
                .contentType("application/json").content(update)).andExpect(status().isOk());
        MvcResult publicContent=mvc.perform(get("/api/v1/public/media/"+id)).andExpect(status().isOk()).andReturn();
        assertThat(publicContent.getResponse().getContentAsByteArray()).containsExactly(pngBytes(32));
        jdbc.update("INSERT INTO news (id,slug,title,summary,body_markdown,category,featured,cover_media_id,status,version) "
                + "VALUES (?,?,?,?,?,?,FALSE,?,'DRAFT',0)",UUID.randomUUID(),"uso","Uso","Resumen","Texto","Institucional",id);
        String makePrivate="{\"version\":1,\"enabled\":true,\"visibility\":\"PRIVATE\"}";
        mvc.perform(put("/api/v1/admin/media/"+id).with(admin()).with(csrf().asHeader())
                .contentType("application/json").content(makePrivate)).andExpect(status().isConflict());
    }

    @Test void rejectsMismatchedTypesAndOversizedImages() throws Exception {
        MockMultipartFile html=new MockMultipartFile("file","imagen.jpg","image/jpeg","<html>no</html>".getBytes());
        mvc.perform(multipart("/api/v1/admin/media").file(html).param("visibility","PUBLIC").with(admin()).with(csrf().asHeader()))
                .andExpect(status().isUnsupportedMediaType());
        MockMultipartFile svg=new MockMultipartFile("file","imagen.svg","image/svg+xml","<svg/>".getBytes());
        mvc.perform(multipart("/api/v1/admin/media").file(svg).param("visibility","PUBLIC").with(admin()).with(csrf().asHeader()))
                .andExpect(status().isUnsupportedMediaType());
        MockMultipartFile tooLarge=new MockMultipartFile("file","imagen.png","image/png",pngBytes(5*1024*1024+1));
        mvc.perform(multipart("/api/v1/admin/media").file(tooLarge).param("visibility","PUBLIC").with(admin()).with(csrf().asHeader()))
                .andExpect(status().isPayloadTooLarge());
    }

    private byte[] pngBytes(int size) {
        byte[] result=new byte[size]; byte[] signature={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};
        System.arraycopy(signature,0,result,0,signature.length); return result;
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user(new AdminPrincipal(ADMIN_ID,"Administrador","admin-media@efp30.test","unused",List.of("ADMIN"),true));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor editor() {
        return user(new AdminPrincipal(ADMIN_ID,"Editor","editor-media@efp30.test","unused",List.of("EDITOR"),true));
    }
    private JsonNode json(String text) throws Exception { return mapper.readTree(text); }
}
