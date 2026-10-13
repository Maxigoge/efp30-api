package ar.edu.efp30.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record CsrfTokenResponse(String token, String headerName) {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank String password) {
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record SessionUser(UUID id, String displayName, String email, List<String> roles) {
    }

    public record SessionResponse(SessionUser user) {
    }
}
