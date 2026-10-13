package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminUserDtos.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public Page<User> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) Boolean enabled,
                           @RequestParam(required = false) String role) {
        return service.list(page, size, enabled, role);
    }

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public User get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
