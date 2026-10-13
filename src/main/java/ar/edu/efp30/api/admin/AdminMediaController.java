package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminMediaDtos.*;

@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {
    private final AdminMediaService service;

    public AdminMediaController(AdminMediaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MediaAdmin> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String visibility,
                                 @RequestParam(required = false) Boolean enabled) {
        return service.list(page, size, visibility, enabled);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAdmin> upload(@RequestPart("file") MultipartFile file,
                                             @RequestParam String visibility) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file, visibility));
    }

    @GetMapping("/{id}")
    public MediaAdmin get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MediaAdmin update(@PathVariable UUID id, @Valid @RequestBody MediaUpdateRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) throws IOException {
        AdminMediaService.StoredMedia stored = service.privateContent(id);
        String filename = stored.metadata().originalName()
                .replace("\"", "_")
                .replace("\r", "")
                .replace("\n", "");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.metadata().mimeType()))
                .contentLength(stored.metadata().sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(Files.newInputStream(stored.path())));
    }
}

