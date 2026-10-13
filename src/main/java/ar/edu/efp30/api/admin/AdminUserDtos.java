package ar.edu.efp30.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record User(@NotNull UUID id, @NotBlank String displayName, @Email String email,
                       @NotEmpty List<String> roles, boolean enabled, @NotNull OffsetDateTime createdAt,
                       @Min(0) long version) {
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String displayName,
                                @NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 12, max = 128) String password,
                                @NotEmpty @Size(min = 1, max = 2) List<String> roles) {
    }

    public record UpdateRequest(@Min(0) long version,
                                @NotBlank @Size(max = 120) String displayName,
                                @NotEmpty @Size(min = 1, max = 2) List<String> roles,
                                boolean enabled) {
    }

    public record ResetPasswordRequest(@Min(0) long version,
                                       @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }
}
