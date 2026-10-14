package ar.edu.efp30.api.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;

public final class DatabaseJdbcTemplate extends JdbcTemplate {
    public DatabaseJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public int update(String sql, Object... args) {
        return super.update(sql, DatabaseParameters.normalize(args));
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return super.query(sql, rowMapper, DatabaseParameters.normalize(args));
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        return super.queryForObject(sql, requiredType, DatabaseParameters.normalize(args));
    }
}
