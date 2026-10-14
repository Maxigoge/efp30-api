package ar.edu.efp30.api.ops;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ReadinessProbe {
    private static final URI READINESS_URI = URI.create("http://127.0.0.1:8080/actuator/health/readiness");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private ReadinessProbe() { }

    public static void main(String[] args) {
        if (!isReady(READINESS_URI)) {
            System.exit(1);
        }
    }

    static boolean isReady(URI endpoint) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
