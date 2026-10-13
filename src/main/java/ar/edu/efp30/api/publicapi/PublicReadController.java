package ar.edu.efp30.api.publicapi;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import static ar.edu.efp30.api.publicapi.PublicContentDtos.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicReadController {

    private final PublicContentService service;

    public PublicReadController(PublicContentService service) {
        this.service = service;
    }

    @GetMapping("/portada/carrusel")
    public Carousel getHomepageCarousel() {
        return service.carousel();
    }

    @GetMapping("/noticias")
    public Page<NewsSummary> listNews(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) Boolean featured) {
        return service.listNews(page, size, q, category, featured);
    }

    @GetMapping("/noticias/{slug}")
    public News getNews(@PathVariable String slug) {
        return service.getNews(slug);
    }

    @GetMapping("/cursos")
    public Page<CourseSummary> listCourses(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String familyId) {
        return service.listCourses(page, size, q, familyId);
    }

    @GetMapping("/cursos/{slug}")
    public Course getCourse(@PathVariable String slug) {
        return service.getCourse(slug);
    }

    @GetMapping("/familias")
    public FamilyList listFamilies() {
        return service.listFamilies();
    }

    @GetMapping("/institucion")
    public Institution getInstitution() {
        return service.getInstitution();
    }

    @GetMapping("/eventos")
    public Page<Event> listEvents(@RequestParam String from,
                                  @RequestParam String to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.listEvents(from, to, page, size);
    }

    @GetMapping("/eventos/{slug}")
    public Event getEvent(@PathVariable String slug) {
        return service.getEvent(slug);
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<InputStreamResource> getPublicMedia(@PathVariable String id) throws IOException {
        PublicContentService.ResolvedMedia resolved = service.getPublicMedia(id);
        var media = resolved.media();
        String fileName = safeFileName(media.originalName());
        String disposition = "application/pdf".equals(media.mimeType()) ? "attachment" : "inline";
        InputStreamResource body = new InputStreamResource(Files.newInputStream(resolved.path()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.mimeType()))
                .contentLength(media.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder(disposition).filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(body);
    }

    private String safeFileName(String originalName) {
        String safe = originalName == null ? "archivo" : originalName
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "_")
                .trim();
        return safe.isBlank() ? "archivo" : safe;
    }
}
