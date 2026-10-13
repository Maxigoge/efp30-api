package ar.edu.efp30.api.auth;

import ar.edu.efp30.api.publicapi.PublicApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static ar.edu.efp30.api.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final boolean secureCookie;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository,
                          JdbcTemplate jdbc,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptLimiter loginAttemptLimiter,
                          @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken, HttpServletRequest request, HttpServletResponse response) {
        String token = csrfToken.getToken();
        csrfTokenRepository.saveToken(new SerializableCsrfToken(
                "X-CSRF-TOKEN", csrfToken.getParameterName(), token), request, response);
        return new CsrfTokenResponse(token, "X-CSRF-TOKEN");
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest body,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        Authentication authentication;
        String email = body.email().trim().toLowerCase();
        loginAttemptLimiter.check(request.getRemoteAddr(), email);
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException exception) {
            loginAttemptLimiter.failed(request.getRemoteAddr(), email);
            throw PublicApiException.unauthorized();
        }
        loginAttemptLimiter.succeeded(request.getRemoteAddr(), email);
        if (request.getSession(false) == null) {
            request.getSession(true);
        }
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
        return sessionResponse((AdminPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public SessionResponse current(@org.springframework.security.core.annotation.AuthenticationPrincipal AdminPrincipal user) {
        return sessionResponse(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
        ResponseCookie expired = ResponseCookie.from("EFP30_SESSION", "")
                .path("/")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest body,
                                               @org.springframework.security.core.annotation.AuthenticationPrincipal AdminPrincipal user,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        if (!passwordEncoder.matches(body.currentPassword(), user.passwordHash())) {
            throw PublicApiException.badRequest("currentPassword", "La contraseña actual no coincide.");
        }
        String encoded = passwordEncoder.encode(body.newPassword());
        int changed = jdbc.update("UPDATE admin_user SET password_hash = ?, version = version + 1, "
                        + "updated_at = now() WHERE id = ?",
                encoded, user.id());
        if (changed != 1) {
            throw PublicApiException.unauthorized();
        }
        AdminPrincipal refreshed = new AdminPrincipal(user.id(), user.displayName(), user.email(), encoded,
                user.roles(), user.enabled());
        Authentication updatedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                refreshed, encoded, refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(updatedAuthentication);
        securityContextRepository.saveContext(context, request, response);
        HttpSession session = request.getSession(false);
        String currentSessionId = session == null ? "" : session.getId();
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ? AND SESSION_ID <> ?",
                user.email(), currentSessionId);
        return ResponseEntity.noContent().build();
    }

    private SessionResponse sessionResponse(AdminPrincipal principal) {
        List<String> roles = principal.roles().stream().sorted().toList();
        return new SessionResponse(new SessionUser(principal.id(), principal.displayName(), principal.email(), roles));
    }
}
