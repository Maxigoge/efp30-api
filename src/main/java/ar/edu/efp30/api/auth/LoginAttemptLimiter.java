package ar.edu.efp30.api.auth;

import ar.edu.efp30.api.publicapi.PublicApiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public void check(String remoteAddress, String email) {
        Instant now = Instant.now();
        long retryAfter = keys(remoteAddress, email).stream()
                .map(windows::get)
                .filter(window -> window != null && window.blockedUntil() != null && now.isBefore(window.blockedUntil()))
                .mapToLong(window -> Math.max(1, Duration.between(now, window.blockedUntil()).toSeconds() + 1))
                .max().orElse(0);
        if (retryAfter > 0) {
            throw PublicApiException.rateLimited(retryAfter);
        }
    }

    public void failed(String remoteAddress, String email) {
        Instant now = Instant.now();
        for (String key : keys(remoteAddress, email)) {
            windows.compute(key, (ignored, current) -> {
                if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
                    return new AttemptWindow(now, 1, null);
                }
                int count = current.count() + 1;
                Instant blockedUntil = count >= MAX_ATTEMPTS ? current.startedAt().plus(WINDOW) : null;
                return new AttemptWindow(current.startedAt(), count, blockedUntil);
            });
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        }
    }

    public void succeeded(String remoteAddress, String email) {
        keys(remoteAddress, email).forEach(windows::remove);
    }

    private List<String> keys(String remoteAddress, String email) {
        String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        return List.of("ip:" + address, "account:" + email.trim().toLowerCase());
    }

    private record AttemptWindow(Instant startedAt, int count, Instant blockedUntil) {
    }
}
