package ar.edu.efp30.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public AdminUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username.trim().toLowerCase();
        var users = jdbc.query("""
                        SELECT id, display_name, email, password_hash, enabled
                        FROM admin_user WHERE lower(email) = ?
                        """,
                (rs, row) -> new AdminPrincipal(rs.getObject("id", java.util.UUID.class),
                        rs.getString("display_name"), rs.getString("email"), rs.getString("password_hash"),
                        List.of(), rs.getBoolean("enabled")), email);
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Administrator account not found");
        }
        AdminPrincipal user = users.getFirst();
        List<String> roles = jdbc.query("SELECT role FROM admin_user_role WHERE user_id = ? ORDER BY role",
                (rs, row) -> rs.getString("role"), user.id());
        return new AdminPrincipal(user.id(), user.displayName(), user.email(), user.passwordHash(), roles, user.enabled());
    }
}
