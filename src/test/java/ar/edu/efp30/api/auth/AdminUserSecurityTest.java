package ar.edu.efp30.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserSecurityTest {

    private static final UUID ADMIN_ID = UUID.fromString("77777777-7777-4777-8777-777777777701");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void insertLastAdmin() {
        jdbc.update("DELETE FROM admin_user_role");
        jdbc.update("DELETE FROM admin_user");
        jdbc.update("INSERT INTO admin_user (id, display_name, email, password_hash, enabled) VALUES (?, ?, ?, ?, TRUE)",
                ADMIN_ID, "Administrador", "admin@efp30.test", passwordEncoder.encode("password-seguro-123"));
        jdbc.update("INSERT INTO admin_user_role (user_id, role) VALUES (?, 'ADMIN')", ADMIN_ID);
    }

    @Test
    void requiresAuthenticationAndRestrictsUsersToAdmins() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith("application/problem+json"))
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/admin/users").with(user("editora@efp30.test").roles("EDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("FORBIDDEN"));

        mvc.perform(get("/api/v1/admin/users").with(user("admin@efp30.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString())
                        .path("items").get(0).path("email").asText()).isEqualTo("admin@efp30.test"));
    }

    @Test
    void csrfProtectsWritesAndAdminCanCreateHashedUserWithoutReturningSecrets() throws Exception {
        String body = """
                {"displayName":"Editora","email":"editora@efp30.test","password":"frase-segura-de-prueba",
                 "roles":["EDITOR"]}
                """;
        mvc.perform(post("/api/v1/admin/users").with(user("admin@efp30.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("CSRF_INVALID"));

        mvc.perform(post("/api/v1/admin/users").with(user("editora@efp30.test").roles("EDITOR"))
                        .with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("FORBIDDEN"));

        var created = mvc.perform(post("/api/v1/admin/users").with(user("admin@efp30.test").roles("ADMIN"))
                        .with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode result = json(created.getResponse().getContentAsString());
        assertThat(result.path("roles").get(0).asText()).isEqualTo("EDITOR");
        assertThat(result.has("password")).isFalse();
        assertThat(result.has("passwordHash")).isFalse();
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM admin_user WHERE email = ?",
                String.class, "editora@efp30.test");
        assertThat(passwordEncoder.matches("frase-segura-de-prueba", storedHash)).isTrue();
        assertThat(storedHash).isNotEqualTo("frase-segura-de-prueba");
    }

    @Test
    void lastAdminAndOptimisticVersionAreProtected() throws Exception {
        String removeLastAdmin = """
                {"version":0,"displayName":"Administrador","roles":["EDITOR"],"enabled":true}
                """;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/users/" + ADMIN_ID)
                        .with(user("admin@efp30.test").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(removeLastAdmin))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("LAST_ADMIN"));

        String staleVersion = """
                {"version":4,"displayName":"Administrador","roles":["ADMIN"],"enabled":true}
                """;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/users/" + ADMIN_ID)
                        .with(user("admin@efp30.test").roles("ADMIN")).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(staleVersion))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("VERSION_CONFLICT"));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
