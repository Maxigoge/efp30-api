package ar.edu.efp30.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class DatabasePasswordFileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final int MAX_SECRET_BYTES = 4096;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String fileValue = environment.getProperty("DATABASE_PASSWORD_FILE");
        if (fileValue == null || fileValue.isBlank()) {
            return;
        }
        String passwordValue = environment.getProperty("DATABASE_PASSWORD");
        if (passwordValue != null && !passwordValue.isEmpty()) {
            throw new IllegalStateException("Set only one of DATABASE_PASSWORD or DATABASE_PASSWORD_FILE");
        }
        String password = readPassword(Path.of(fileValue.trim()));
        environment.getPropertySources().addFirst(new MapPropertySource("databasePasswordFile",
                Map.of("spring.datasource.password", password)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static String readPassword(Path path) {
        try {
            if (!Files.isRegularFile(path) || !Files.isReadable(path) || Files.size(path) > MAX_SECRET_BYTES) {
                throw new IllegalStateException("DATABASE_PASSWORD_FILE must name a readable regular file no larger than 4096 bytes");
            }
            String value = Files.readString(path, StandardCharsets.UTF_8);
            while (value.endsWith("\n") || value.endsWith("\r")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.isEmpty() || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalStateException("DATABASE_PASSWORD_FILE must contain one non-empty line");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read DATABASE_PASSWORD_FILE", exception);
        }
    }
}
