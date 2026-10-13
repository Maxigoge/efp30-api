package ar.edu.efp30.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String displayName;
    private final String password;

    public AdminBootstrapRunner(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                                @Value("${BOOTSTRAP_ADMIN_NAME:}") String displayName,
                                @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.displayName = displayName.trim();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean anyValue = !email.isBlank() || !displayName.isBlank() || !password.isBlank();
        if (!anyValue) {
            return;
        }
        if (email.isBlank() || email.length() > 254 || !EMAIL.matcher(email).matches()
                || displayName.isBlank() || displayName.length() > 120
                || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("Bootstrap admin requires a valid email, display name, and 12-128 character password");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user_role r "
                + "JOIN admin_user u ON u.id = r.user_id WHERE r.role = 'ADMIN' AND u.enabled = TRUE", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Bootstrap admin skipped because an enabled ADMIN account already exists");
            return;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO admin_user (id, display_name, email, password_hash, enabled) VALUES (?, ?, ?, ?, TRUE)",
                id, displayName, email, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO admin_user_role (user_id, role) VALUES (?, 'ADMIN')", id);
        log.info("Bootstrap ADMIN account created for {}", email);
    }
}
