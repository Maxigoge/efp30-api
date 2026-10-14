package ar.edu.efp30.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabasePasswordFileEnvironmentPostProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void readsDockerSecretAndRemovesOnlyTrailingLineEnding() throws Exception {
        Path secret = tempDir.resolve("mysql_app_password");
        Files.writeString(secret, "a secure secret  \r\n");

        assertThat(DatabasePasswordFileEnvironmentPostProcessor.readPassword(secret)).isEqualTo("a secure secret  ");
    }

    @Test
    void exposesTheFileContentsOnlyAsTheDatasourcePasswordProperty() throws Exception {
        Path secret = tempDir.resolve("mysql_app_password");
        Files.writeString(secret, "test-only-password\n");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_PASSWORD_FILE", secret.toString());

        new DatabasePasswordFileEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("test-only-password");
        assertThat(environment.getProperty("DATABASE_PASSWORD_FILE")).isEqualTo(secret.toString());
    }
}
