package ar.edu.efp30.api.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class DatabaseIds {
    private DatabaseIds() { }

    public static String value(UUID id) {
        return id == null ? null : id.toString();
    }

    public static UUID read(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }
}
