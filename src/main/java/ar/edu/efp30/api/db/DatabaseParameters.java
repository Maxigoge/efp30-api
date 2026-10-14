package ar.edu.efp30.api.db;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

public final class DatabaseParameters {
    private DatabaseParameters() { }

    public static Object[] normalize(Object... values) {
        return Arrays.stream(values).map(DatabaseParameters::normalize).toArray();
    }

    private static Object normalize(Object value) {
        if (value instanceof UUID id) {
            return id.toString();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return DatabaseTimes.utc(dateTime);
        }
        return value;
    }
}
