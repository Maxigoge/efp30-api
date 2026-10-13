package ar.edu.efp30.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminEditorialDtos {
    private AdminEditorialDtos() { }

    public record SlugCreateRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug) { }

    public record NewsCreateRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 400) String summary,
            @NotBlank @Size(max = 50000) String bodyMarkdown,
            UUID coverMediaId,
            @NotBlank @Size(max = 60) String category,
            boolean featured) { }

    public record NewsUpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 400) String summary,
            @NotBlank @Size(max = 50000) String bodyMarkdown,
            UUID coverMediaId,
            @NotBlank @Size(max = 60) String category,
            boolean featured) { }

    public record CourseCreateRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max = 180) String title,
            @NotNull UUID familyId,
            @NotBlank @Size(max = 400) String summary,
            @NotBlank @Size(max = 50000) String bodyMarkdown,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 240) String> requirements,
            @Min(1) @Max(10000) Integer durationHours,
            @NotBlank @Pattern(regexp = "PRESENTIAL|ONLINE|HYBRID") String modality,
            @NotBlank @Pattern(regexp = "CONSULTATION|OPEN|CLOSED") String enrollmentStatus,
            UUID coverMediaId) { }

    public record CourseUpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 180) String title,
            @NotNull UUID familyId,
            @NotBlank @Size(max = 400) String summary,
            @NotBlank @Size(max = 50000) String bodyMarkdown,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 240) String> requirements,
            @Min(1) @Max(10000) Integer durationHours,
            @NotBlank @Pattern(regexp = "PRESENTIAL|ONLINE|HYBRID") String modality,
            @NotBlank @Pattern(regexp = "CONSULTATION|OPEN|CLOSED") String enrollmentStatus,
            UUID coverMediaId) { }

    public record EventCreateRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull OffsetDateTime startsAt,
            @NotNull OffsetDateTime endsAt,
            @NotBlank @Pattern(regexp = "America/Argentina/Buenos_Aires") String timezone,
            @NotBlank @Size(max = 240) String location,
            boolean allDay) { }

    public record EventUpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull OffsetDateTime startsAt,
            @NotNull OffsetDateTime endsAt,
            @NotBlank @Pattern(regexp = "America/Argentina/Buenos_Aires") String timezone,
            @NotBlank @Size(max = 240) String location,
            boolean allDay) { }

    public record FamilyCreateRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max = 120) String name) { }

    public record FamilyUpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 120) String name,
            boolean active) { }

    public record VersionRequest(@NotNull @Min(0) Long version) { }

    public record SocialLinkInput(@NotBlank @Size(max = 60) String label,
                                  @NotBlank @Size(max = 2048) String url) { }

    public record InstitutionUpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 180) String name,
            @NotNull @Size(max = 4000) String description,
            @NotNull @Size(max = 240) String address,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(max = 10) List<@Size(max = 40) String> phones,
            @NotNull @Size(max = 20) List<@Valid SocialLinkInput> socialLinks) { }

    public record NewsAdmin(UUID id, String slug, String title, String summary, String bodyMarkdown,
                            UUID coverMediaId, String category, boolean featured, String status,
                            OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                            long version) { }

    public record CourseAdmin(UUID id, String slug, String title, UUID familyId, String summary,
                              String bodyMarkdown, List<String> requirements, Integer durationHours,
                              String modality, String enrollmentStatus, UUID coverMediaId, String status,
                              OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              long version) { }

    public record EventAdmin(UUID id, String slug, String title, String description, OffsetDateTime startsAt,
                             OffsetDateTime endsAt, String timezone, String location, boolean allDay, String status,
                             OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                             long version) { }

    public record FamilyAdmin(UUID id, String slug, String name, boolean active, long version) { }

    public record CoursePhotoInput(UUID id, @NotNull UUID mediaId, @NotBlank @Size(max = 240) String alt,
                                   @Min(0) @Max(99) int position, boolean enabled, boolean showOnHomepage) { }
    public record CoursePhotosUpdateRequest(@NotNull @Min(0) Long version,
                                            @NotNull @Size(max = 100) List<@Valid CoursePhotoInput> photos) { }
    public record CoursePhotosResponse(UUID courseId, long version, List<CoursePhotoInput> photos) { }
    public record InstitutionAdmin(String name, String description, String address, String email,
                                   List<String> phones, List<SocialLinkInput> socialLinks, long version) { }

    public record EditorialAction(String status, OffsetDateTime publishedAt, long version) { }
}



