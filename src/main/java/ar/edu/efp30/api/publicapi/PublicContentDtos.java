package ar.edu.efp30.api.publicapi;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PublicContentDtos {

    private PublicContentDtos() {
    }

    public record Page<T>(List<T> items, int page, int size, long totalItems, long totalPages) {
    }

    public record NewsSummary(UUID id, String slug, String title, String summary, String coverUrl,
                              String category, boolean featured, OffsetDateTime publishedAt) {
    }

    public record News(UUID id, String slug, String title, String summary, String bodyMarkdown, String coverUrl,
                       String category, boolean featured, OffsetDateTime publishedAt) {
    }

    public record Family(UUID id, String slug, String name) {
    }

    public record FamilyList(List<Family> items) {
    }

    public record FamilyReference(UUID id, String slug, String name) {
    }

    public record CourseSummary(UUID id, String slug, String title, FamilyReference family, String summary,
                                Integer durationHours, String modality, String enrollmentStatus, String coverUrl) {
    }

    public record CoursePhoto(UUID id, String imageUrl, String alt, int position) {
    }

    public record Course(UUID id, String slug, String title, FamilyReference family, String summary,
                         String bodyMarkdown, List<String> requirements, Integer durationHours, String modality,
                         String enrollmentStatus, String coverUrl, List<CoursePhoto> photos) {
    }

    public record Event(UUID id, String slug, String title, String description, OffsetDateTime startsAt,
                        OffsetDateTime endsAt, String timezone, String location, boolean allDay) {
    }

    public record CarouselItem(UUID id, UUID courseId, String courseSlug, String courseTitle, UUID mediaId,
                               String imageUrl, String alt, int position) {
    }

    public record Carousel(List<CarouselItem> items) {
    }

    public record SocialLink(String label, String url) {
    }

    public record Institution(String name, String description, String address, String email, List<String> phones,
                              List<SocialLink> socialLinks) {
    }

    public record MediaFile(UUID id, String objectKey, String originalName, String mimeType, long sizeBytes) {
    }
}
