package ar.edu.efp30.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthEndpointTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void exposesLivenessAndDatabaseReadinessWithoutInternalDetails() {
        var liveness = http.getForEntity("/actuator/health/liveness", String.class);
        var readiness = http.getForEntity("/actuator/health/readiness", String.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).contains("\"status\":\"UP\"");
        assertThat(liveness.getBody()).doesNotContain("components");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"");
    }
}
