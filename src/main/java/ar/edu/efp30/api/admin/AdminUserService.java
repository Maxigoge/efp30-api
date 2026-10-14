package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.db.DatabaseIds;
import ar.edu.efp30.api.db.DatabaseTimes;
import ar.edu.efp30.api.publicapi.PublicApiException;
import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminUserDtos.*;

@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "EDITOR");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<User> list(int page, int size, Boolean enabled, String role) {
        validatePage(page, size);
        validateRoleFilter(role);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (enabled != null) {
            where.append(" AND u.enabled = ?");
            args.add(enabled);
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM admin_user_role r WHERE r.user_id = u.id AND r.role = ?)");
            args.add(role);
        }
        String from = " FROM admin_user u";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((long) page * size);
        List<UUID> ids = jdbc.query("SELECT u.id" + from + where
                        + " ORDER BY u.created_at DESC, u.id ASC LIMIT ? OFFSET ?",
                (rs, row) -> DatabaseIds.read(rs, "id"), queryArgs.toArray());
        List<User> users = ids.stream().map(this::get).toList();
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new Page<>(users, page, size, total, pages);
    }

    public User get(UUID id) {
        List<UserRow> rows = jdbc.query("""
                        SELECT id, display_name, email, enabled, created_at, version
                        FROM admin_user WHERE id = ?
                        """,
                (rs, row) -> new UserRow(DatabaseIds.read(rs, "id"), rs.getString("display_name"),
                        rs.getString("email"), rs.getBoolean("enabled"), argentina(rs), rs.getLong("version")), id);
        if (rows.isEmpty()) {
            throw PublicApiException.notFound();
        }
        UserRow user = rows.getFirst();
        return new User(user.id(), user.displayName(), user.email(), roles(user.id()), user.enabled(),
                user.createdAt(), user.version());
    }

    @Transactional
    public User create(CreateRequest request) {
        List<String> roles = validateRoles(request.roles());
        String email = request.email().trim().toLowerCase();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO admin_user (id, display_name, email, password_hash, enabled) "
                            + "VALUES (?, ?, ?, ?, TRUE)",
                    id, request.displayName().trim(), email, passwordEncoder.encode(request.password()));
            insertRoles(id, roles);
        } catch (DuplicateKeyException exception) {
            throw PublicApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese email.");
        }
        return get(id);
    }

    @Transactional
    public User update(UUID id, UpdateRequest request) {
        User current = get(id);
        checkVersion(current.version(), request.version());
        List<String> roles = validateRoles(request.roles());
        boolean retainsAdmin = request.enabled() && roles.contains("ADMIN");
        if (current.enabled() && current.roles().contains("ADMIN") && !retainsAdmin && activeAdminCount() <= 1) {
            throw PublicApiException.conflict("LAST_ADMIN", "No se puede quitar o deshabilitar el último ADMIN activo.");
        }
        int changed = jdbc.update("UPDATE admin_user SET display_name = ?, enabled = ?, version = version + 1, "
                        + "updated_at = now() WHERE id = ? AND version = ?",
                request.displayName().trim(), request.enabled(), id, request.version());
        if (changed != 1) {
            throw versionConflict();
        }
        jdbc.update("DELETE FROM admin_user_role WHERE user_id = ?", id);
        insertRoles(id, roles);
        invalidateSessions(current.email());
        return get(id);
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User current = get(id);
        checkVersion(current.version(), request.version());
        int changed = jdbc.update("UPDATE admin_user SET password_hash = ?, version = version + 1, updated_at = now() "
                        + "WHERE id = ? AND version = ?",
                passwordEncoder.encode(request.newPassword()), id, request.version());
        if (changed != 1) {
            throw versionConflict();
        }
        invalidateSessions(current.email());
    }

    private List<String> roles(UUID userId) {
        return jdbc.query("SELECT role FROM admin_user_role WHERE user_id = ? ORDER BY role",
                (rs, row) -> rs.getString("role"), userId);
    }

    private void insertRoles(UUID userId, List<String> roles) {
        roles.forEach(role -> jdbc.update("INSERT INTO admin_user_role (user_id, role) VALUES (?, ?)", userId, role));
    }

    private List<String> validateRoles(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 2
                || values.stream().anyMatch(role -> !ALLOWED_ROLES.contains(role))
                || values.stream().distinct().count() != values.size()) {
            throw PublicApiException.badRequest("roles", "Debe incluir roles únicos ADMIN o EDITOR.");
        }
        return values.stream().sorted().toList();
    }

    private void validateRoleFilter(String role) {
        if (role != null && !role.isBlank() && !ALLOWED_ROLES.contains(role)) {
            throw PublicApiException.badRequest("role", "El rol debe ser ADMIN o EDITOR.");
        }
    }

    private int activeAdminCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM admin_user u "
                + "JOIN admin_user_role r ON r.user_id = u.id "
                + "WHERE u.enabled = TRUE AND r.role = 'ADMIN'", Integer.class);
        return count == null ? 0 : count;
    }

    private void invalidateSessions(String email) {
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", email);
    }

    private void checkVersion(long current, long supplied) {
        if (supplied != current) {
            throw versionConflict();
        }
    }

    private PublicApiException versionConflict() {
        return PublicApiException.conflict("VERSION_CONFLICT", "El recurso cambió; volvé a cargarlo antes de guardar.");
    }

    private OffsetDateTime argentina(ResultSet rs) throws SQLException {
        return DatabaseTimes.argentina(rs, "created_at");
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw PublicApiException.badRequest("page", "Debe ser mayor o igual a cero.");
        }
        if (size < 1 || size > 100) {
            throw PublicApiException.badRequest("size", "Debe estar entre 1 y 100.");
        }
    }

    private record UserRow(UUID id, String displayName, String email, boolean enabled, OffsetDateTime createdAt,
                           long version) {
    }
}
