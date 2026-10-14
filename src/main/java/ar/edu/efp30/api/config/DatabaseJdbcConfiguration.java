package ar.edu.efp30.api.config;

import ar.edu.efp30.api.db.DatabaseJdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseJdbcConfiguration {
    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new DatabaseJdbcTemplate(dataSource);
    }
}
