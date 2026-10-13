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
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

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
    void insertAdmin() {
        jdbc.update("DELETE FROM admin_user_role");
        jdbc.update("DELETE FROM admin_user");
        jdbc.update("INSERT INTO admin_user (id, display_name, email, password_hash, enabled) VALUES (?, ?, ?, ?, TRUE)",
                ADMIN_ID, "Administrador", "admin@efp30.test", passwordEncoder.encode("password-seguro-123"));
        jdbc.update("INSERT INTO admin_user_role (user_id, role) VALUES (?, 'ADMIN')", ADMIN_ID);
    }

    @Test
    void csrfLoginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        CsrfSession initial = getCsrf();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(cookie(initial.cookieValue()))
                        .header("X-CSRF-TOKEN", initial.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@efp30.test\",\"password\":\"password-seguro-123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = json(login.getResponse().getContentAsString());
        assertThat(loginBody.path("user").path("email").asText()).isEqualTo("admin@efp30.test");
        assertThat(loginBody.path("user").path("roles").get(0).asText()).isEqualTo("ADMIN");
        assertThat(loginBody.toString()).doesNotContain("passwordHash", "password-seguro");
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("EFP30_SESSION").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(setCookie).doesNotContain("Domain=");

        String authenticatedCookie = sessionCookie(login.getResponse());
        assertThat(authenticatedCookie).isNotEqualTo(initial.cookieValue());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(authenticatedCookie)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString())
                        .path("user").path("displayName").asText()).isEqualTo("Administrador"));

        CsrfSession refreshed = getCsrf(authenticatedCookie);
        assertThat(refreshed.token()).isNotEqualTo(initial.token());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie(authenticatedCookie))
                        .header("X-CSRF-TOKEN", refreshed.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(authenticatedCookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsAreGenericAndCurrentPasswordChangeKeepsCurrentSession() throws Exception {
        CsrfSession csrf = getCsrf();
        MvcResult denied = mvc.perform(post("/api/v1/auth/login").cookie(cookie(csrf.cookieValue()))
                        .header("X-CSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@efp30.test\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode deniedBody = json(denied.getResponse().getContentAsString());
        assertThat(deniedBody.path("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(deniedBody.path("detail").asText()).doesNotContain("email", "password");

        CsrfSession loginCsrf = getCsrf();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(cookie(loginCsrf.cookieValue()))
                        .header("X-CSRF-TOKEN", loginCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@efp30.test\",\"password\":\"password-seguro-123\"}"))
                .andExpect(status().isOk()).andReturn();
        String sessionCookie = sessionCookie(login.getResponse());
        CsrfSession fresh = getCsrf(sessionCookie);
        mvc.perform(post("/api/v1/auth/password").cookie(cookie(sessionCookie)).header("X-CSRF-TOKEN", fresh.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password-seguro-123\",\"newPassword\":\"nueva-frase-segura-456\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(sessionCookie))).andExpect(status().isOk());
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM admin_user WHERE id = ?", String.class, ADMIN_ID);
        assertThat(passwordEncoder.matches("nueva-frase-segura-456", storedHash)).isTrue();
    }

    @Test
    void writesWithoutCsrfAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@efp30.test\",\"password\":\"password-seguro-123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                        .isEqualTo("CSRF_INVALID"));
    }

    @Test
    void rateLimitsRepeatedFailuresByAccountAndAddress() throws Exception {
        CsrfSession csrf = getCsrf();
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login").cookie(cookie(csrf.cookieValue()))
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.55");
                                return request;
                            })
                            .header("X-CSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"rate-limit@efp30.test\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").cookie(cookie(csrf.cookieValue()))
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.55");
                            return request;
                        })
                        .header("X-CSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rate-limit@efp30.test\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
                    assertThat(json(result.getResponse().getContentAsString()).path("code").asText())
                            .isEqualTo("RATE_LIMITED");
                });
    }

    private CsrfSession getCsrf() throws Exception {
        return getCsrf(null);
    }

    private CsrfSession getCsrf(String existingCookie) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (existingCookie != null) {
            request.cookie(cookie(existingCookie));
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        String token = json(result.getResponse().getContentAsString()).path("token").asText();
        String cookieValue = sessionCookieOrExisting(result.getResponse(), existingCookie);
        return new CsrfSession(token, cookieValue);
    }

    private String sessionCookieOrExisting(org.springframework.mock.web.MockHttpServletResponse response,
                                           String existingCookie) {
        String header = response.getHeader("Set-Cookie");
        return header == null ? existingCookie : header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private String sessionCookie(org.springframework.mock.web.MockHttpServletResponse response) {
        String value = sessionCookieOrExisting(response, null);
        assertThat(value).isNotBlank();
        return value;
    }

    private Cookie cookie(String value) {
        return new Cookie("EFP30_SESSION", value);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private record CsrfSession(String token, String cookieValue) {
    }
}
