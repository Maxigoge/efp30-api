package ar.edu.efp30.api.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public final class AdminMediaDtos {
    private AdminMediaDtos() { }
    public record MediaUpdateRequest(@NotNull @Min(0) Long version, boolean enabled,
                                     @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE") String visibility) { }
    public record MediaAdmin(UUID id, String originalName, String mimeType, long sizeBytes,
                             String visibility, boolean enabled, String url, long version) { }
}
