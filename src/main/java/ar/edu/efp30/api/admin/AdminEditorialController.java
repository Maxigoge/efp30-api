package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.auth.AdminPrincipal;
import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminEditorialDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminEditorialController {
    private final AdminEditorialService service;

    public AdminEditorialController(AdminEditorialService service) { this.service=service; }

    @GetMapping("/noticias")
    public Page<NewsAdmin> listNews(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                    @RequestParam(required=false) String status,@RequestParam(required=false) String q,
                                    @RequestParam(required=false) String category) {
        return service.listNews(page,size,status,q,category);
    }
    @PostMapping("/noticias")
    public ResponseEntity<NewsAdmin> createNews(@Valid @RequestBody NewsCreateRequest request,
                                                 @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createNews(request,actor));
    }
    @GetMapping("/noticias/{id}") public NewsAdmin getNews(@PathVariable UUID id) { return service.getNews(id); }
    @PutMapping("/noticias/{id}")
    public NewsAdmin updateNews(@PathVariable UUID id,@Valid @RequestBody NewsUpdateRequest request,
                                @AuthenticationPrincipal AdminPrincipal actor) { return service.updateNews(id,request,actor); }
    @PostMapping("/noticias/{id}/publish")
    public Object publishNews(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                              @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("noticias",id,request,actor,true); }
    @PostMapping("/noticias/{id}/archive")
    public Object archiveNews(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                              @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("noticias",id,request,actor,false); }

    @GetMapping("/cursos")
    public Page<CourseAdmin> listCourses(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                         @RequestParam(required=false) String status,@RequestParam(required=false) String q,
                                         @RequestParam(required=false) UUID familyId) {
        return service.listCourses(page,size,status,q,familyId);
    }
    @PostMapping("/cursos")
    public ResponseEntity<CourseAdmin> createCourse(@Valid @RequestBody CourseCreateRequest request,
                                                     @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCourse(request,actor));
    }
    @GetMapping("/cursos/{id}") public CourseAdmin getCourse(@PathVariable UUID id) { return service.getCourse(id); }
    @PutMapping("/cursos/{id}")
    public CourseAdmin updateCourse(@PathVariable UUID id,@Valid @RequestBody CourseUpdateRequest request,
                                    @AuthenticationPrincipal AdminPrincipal actor) { return service.updateCourse(id,request,actor); }
    @PostMapping("/cursos/{id}/publish")
    public Object publishCourse(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                                @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("cursos",id,request,actor,true); }
    @PostMapping("/cursos/{id}/archive")
    public Object archiveCourse(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                                @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("cursos",id,request,actor,false); }
    @GetMapping("/cursos/{id}/photos")
    public CoursePhotosResponse getCoursePhotos(@PathVariable UUID id) { return service.getCoursePhotos(id); }
    @PutMapping("/cursos/{id}/photos")
    public CoursePhotosResponse replaceCoursePhotos(@PathVariable UUID id,@Valid @RequestBody CoursePhotosUpdateRequest request,
                                                    @AuthenticationPrincipal AdminPrincipal actor) {
        return service.replaceCoursePhotos(id,request,actor);
    }

    @GetMapping("/familias")
    public Page<FamilyAdmin> listFamilies(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                           @RequestParam(required=false) Boolean active) { return service.listFamilies(page,size,active); }
    @PostMapping("/familias")
    public ResponseEntity<FamilyAdmin> createFamily(@Valid @RequestBody FamilyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFamily(request));
    }
    @GetMapping("/familias/{id}") public FamilyAdmin getFamily(@PathVariable UUID id) { return service.getFamily(id); }
    @PutMapping("/familias/{id}")
    public FamilyAdmin updateFamily(@PathVariable UUID id,@Valid @RequestBody FamilyUpdateRequest request) {
        return service.updateFamily(id,request);
    }

    @GetMapping("/eventos")
    public Page<EventAdmin> listEvents(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                        @RequestParam(required=false) String status,@RequestParam(required=false) String q) {
        return service.listEvents(page,size,status,q);
    }
    @PostMapping("/eventos")
    public ResponseEntity<EventAdmin> createEvent(@Valid @RequestBody EventCreateRequest request,
                                                   @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEvent(request,actor));
    }
    @GetMapping("/eventos/{id}") public EventAdmin getEvent(@PathVariable UUID id) { return service.getEvent(id); }
    @PutMapping("/eventos/{id}")
    public EventAdmin updateEvent(@PathVariable UUID id,@Valid @RequestBody EventUpdateRequest request,
                                  @AuthenticationPrincipal AdminPrincipal actor) { return service.updateEvent(id,request,actor); }
    @PostMapping("/eventos/{id}/publish")
    public Object publishEvent(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                               @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("eventos",id,request,actor,true); }
    @PostMapping("/eventos/{id}/archive")
    public Object archiveEvent(@PathVariable UUID id,@Valid @RequestBody VersionRequest request,
                               @AuthenticationPrincipal AdminPrincipal actor) { return service.transition("eventos",id,request,actor,false); }

    @GetMapping("/institucion") public InstitutionAdmin getInstitution() { return service.getInstitution(); }
    @PutMapping("/institucion")
    public InstitutionAdmin updateInstitution(@Valid @RequestBody InstitutionUpdateRequest request,
                                              @AuthenticationPrincipal AdminPrincipal actor) {
        return service.updateInstitution(request,actor);
    }
}
