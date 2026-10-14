package ar.edu.efp30.api.ops;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessProbeTest {
    @Test
    void returnsHealthyOnlyForAnHttp200ReadinessResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = "/actuator/health/readiness".equals(exchange.getRequestURI().getPath()) ? 200 : 503;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(ReadinessProbe.isReady(URI.create(baseUrl + "/actuator/health/readiness"))).isTrue();
            assertThat(ReadinessProbe.isReady(URI.create(baseUrl + "/actuator/health/unready"))).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
