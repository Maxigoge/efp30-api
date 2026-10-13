package ar.edu.efp30.api.publicapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import static ar.edu.efp30.api.publicapi.PublicContentDtos.*;

@Service
public class PublicContentService {

    private final PublicContentRepository repository;
    private final Path mediaRoot;

    public PublicContentService(PublicContentRepository repository,
                                @Value("${efp30.media.root:./data/media}") String mediaRoot) {
        this.repository = repository;
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    public Page<NewsSummary> listNews(int page, int size, String query, String category, Boolean featured) {
        validatePage(page, size);
        validateLength("q", query, 120);
        validateLength("category", category, 60);
        return repository.listNews(page, size, query, category, featured);
    }

    public News getNews(String slug) {
        return repository.getNews(slug);
    }

    public Page<CourseSummary> listCourses(int page, int size, String query, String familyId) {
        validatePage(page, size);
        validateLength("q", query, 120);
        UUID parsedFamilyId = null;
        if (familyId != null && !familyId.isBlank()) {
            try {
                parsedFamilyId = UUID.fromString(familyId);
            } catch (IllegalArgumentException exception) {
                throw PublicApiException.badRequest("familyId", "Debe ser un UUID válido.");
            }
        }
        return repository.listCourses(page, size, query, parsedFamilyId);
    }

    public Course getCourse(String slug) {
        return repository.getCourse(slug);
    }

    public FamilyList listFamilies() {
        return repository.listFamilies();
    }

    public Page<Event> listEvents(String fromValue, String toValue, int page, int size) {
        validatePage(page, size);
        OffsetDateTime from = parseOffsetDateTime("from", fromValue);
        OffsetDateTime to = parseOffsetDateTime("to", toValue);
        if (!to.isAfter(from)) {
            throw PublicApiException.badRequest("to", "Debe ser posterior a from.");
        }
        return repository.listEvents(from, to, page, size);
    }

    public Event getEvent(String slug) {
        return repository.getEvent(slug);
    }

    public Institution getInstitution() {
        return repository.institution();
    }

    public Carousel carousel() {
        return repository.carousel();
    }

    public ResolvedMedia getPublicMedia(String idValue) {
        UUID id;
        try {
            id = UUID.fromString(idValue);
        } catch (IllegalArgumentException exception) {
            throw PublicApiException.notFound();
        }
        MediaFile media = repository.getPublicMedia(id);
        try {
            Path relative = Path.of(media.objectKey());
            if (relative.isAbsolute() || relative.getNameCount() != 1 || media.objectKey().contains("..")) {
                throw PublicApiException.notFound();
            }
            Path candidate = mediaRoot.resolve(relative).normalize();
            if (!candidate.startsWith(mediaRoot)) {
                throw PublicApiException.notFound();
            }
            Path realRoot = mediaRoot.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile)
                    || Files.size(realFile) != media.sizeBytes()) {
                throw PublicApiException.notFound();
            }
            return new ResolvedMedia(media, realFile);
        } catch (InvalidPathException | IOException exception) {
            throw PublicApiException.notFound();
        }
    }

    private OffsetDateTime parseOffsetDateTime(String field, String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw PublicApiException.badRequest(field, "Debe ser una fecha ISO 8601 con offset.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw PublicApiException.badRequest("page", "Debe ser mayor o igual a cero.");
        }
        if (size < 1 || size > 100) {
            throw PublicApiException.badRequest("size", "Debe estar entre 1 y 100.");
        }
    }

    private void validateLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw PublicApiException.badRequest(field, "Supera el largo máximo permitido.");
        }
    }

    public record ResolvedMedia(MediaFile media, Path path) {
    }
}
