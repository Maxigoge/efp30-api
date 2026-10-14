package ar.edu.efp30.api.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class DatabaseTimes {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private DatabaseTimes() { }

    public static LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static OffsetDateTime argentina(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.atOffset(ZoneOffset.UTC).atZoneSameInstant(ARGENTINA).toOffsetDateTime();
    }
}
