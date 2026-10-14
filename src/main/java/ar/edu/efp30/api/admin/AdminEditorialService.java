package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.db.DatabaseIds;
import ar.edu.efp30.api.auth.AdminPrincipal;
import ar.edu.efp30.api.db.DatabaseTimes;
import ar.edu.efp30.api.publicapi.PublicApiException;
import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminEditorialDtos.*;

@Service
public class AdminEditorialService {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String NEWS_COLUMNS = "n.id,n.slug,n.title,n.summary,n.body_markdown,n.cover_media_id,"
            + "n.category,n.featured,n.status,n.published_at,n.created_at,n.updated_at,n.version";
    private static final String COURSE_COLUMNS = "c.id,c.slug,c.title,c.family_id,c.summary,c.body_markdown,"
            + "c.duration_hours,c.modality,c.enrollment_status,c.cover_media_id,c.status,c.published_at,"
            + "c.created_at,c.updated_at,c.version";
    private static final String EVENT_COLUMNS = "e.id,e.slug,e.title,e.description,e.starts_at,e.ends_at,e.timezone,"
            + "e.location,e.all_day,e.status,e.published_at,e.created_at,e.updated_at,e.version";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdminEditorialService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Page<NewsAdmin> listNews(int page, int size, String status, String q, String category) {
        validatePage(page, size);
        validateStatus(status);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        addStatus(where, args, "n.status", status);
        addQuery(where, args, q, "n.title", "n.summary");
        if (category != null && !category.isBlank()) { validateFilter(category,"category",60); where.append(" AND n.category=?"); args.add(category.trim()); }
        return page("SELECT " + NEWS_COLUMNS + " FROM news n" + where + " ORDER BY n.updated_at DESC,n.id ASC",
                "SELECT COUNT(*) FROM news n" + where, args, page, size, this::news);
    }

    public NewsAdmin getNews(UUID id) {
        return one("SELECT " + NEWS_COLUMNS + " FROM news n WHERE n.id=?", this::news, id);
    }

    @Transactional
    public NewsAdmin createNews(NewsCreateRequest request, AdminPrincipal actor) {
        validateMarkdown(request.bodyMarkdown(), "bodyMarkdown");
        UUID id = UUID.randomUUID();
         validateOptionalImage(request.coverMediaId());
        try {
            jdbc.update("INSERT INTO news (id,slug,title,summary,body_markdown,cover_media_id,category,featured,status,"
                            + "version,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,'DRAFT',0,?,?)",
                    id, request.slug().trim(), request.title().trim(), request.summary().trim(),
                    request.bodyMarkdown(), request.coverMediaId(), request.category().trim(), request.featured(),
                    actor.id(), actor.id());
        } catch (DuplicateKeyException exception) { throw slugConflict(); }
        return getNews(id);
    }

    @Transactional
    public NewsAdmin updateNews(UUID id, NewsUpdateRequest request, AdminPrincipal actor) {
        NewsAdmin current = getNews(id);
        checkVersion(current.version(), request.version());
        validateMarkdown(request.bodyMarkdown(), "bodyMarkdown");
         validateOptionalImage(request.coverMediaId());
        int changed = jdbc.update("UPDATE news SET title=?,summary=?,body_markdown=?,cover_media_id=?,category=?,"
                        + "featured=?,version=version+1,updated_at=now(),updated_by=? WHERE id=? AND version=?",
                request.title().trim(), request.summary().trim(), request.bodyMarkdown(), request.coverMediaId(),
                request.category().trim(), request.featured(), actor.id(), id, request.version());
        if (changed != 1) throw versionConflict();
        return getNews(id);
    }

    public Page<CourseAdmin> listCourses(int page, int size, String status, String q, UUID familyId) {
        validatePage(page, size); validateStatus(status);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        addStatus(where, args, "c.status", status); addQuery(where, args, q, "c.title", "c.summary");
        if (familyId != null) { where.append(" AND c.family_id=?"); args.add(familyId); }
        Page<UUID> ids = page("SELECT c.id FROM course c" + where + " ORDER BY c.title ASC,c.id ASC",
                "SELECT COUNT(*) FROM course c" + where, args, page, size,
                (rs, row) -> DatabaseIds.read(rs, "id"));
        return new Page<>(ids.items().stream().map(this::getCourse).toList(), page, size,
                ids.totalItems(), ids.totalPages());
    }

    public CourseAdmin getCourse(UUID id) {
        CourseAdmin base = one("SELECT " + COURSE_COLUMNS + " FROM course c WHERE c.id=?", this::course, id);
        List<String> requirements = jdbc.query("SELECT requirement FROM course_requirement WHERE course_id=? ORDER BY position",
                (rs, row) -> rs.getString(1), id);
        return new CourseAdmin(base.id(), base.slug(), base.title(), base.familyId(), base.summary(), base.bodyMarkdown(),
                requirements, base.durationHours(), base.modality(), base.enrollmentStatus(), base.coverMediaId(),
                base.status(), base.publishedAt(), base.createdAt(), base.updatedAt(), base.version());
    }

    @Transactional
    public CourseAdmin createCourse(CourseCreateRequest request, AdminPrincipal actor) {
        validateMarkdown(request.bodyMarkdown(), "bodyMarkdown"); requireFamily(request.familyId());
        validateOptionalImage(request.coverMediaId());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO course (id,slug,title,family_id,summary,body_markdown,duration_hours,modality,"
                            + "enrollment_status,cover_media_id,status,version,created_by,updated_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?)",
                    id, request.slug().trim(), request.title().trim(), request.familyId(), request.summary().trim(),
                    request.bodyMarkdown(), request.durationHours(), request.modality(), request.enrollmentStatus(),
                    request.coverMediaId(), actor.id(), actor.id());
        } catch (DuplicateKeyException exception) { throw slugConflict(); }
        replaceRequirements(id, request.requirements());
        return getCourse(id);
    }

    @Transactional
    public CourseAdmin updateCourse(UUID id, CourseUpdateRequest request, AdminPrincipal actor) {
        CourseAdmin current = getCourse(id); checkVersion(current.version(), request.version());
        validateMarkdown(request.bodyMarkdown(), "bodyMarkdown"); requireFamily(request.familyId());
        if ("PUBLISHED".equals(current.status())) requireActiveFamily(request.familyId());
         validateOptionalImage(request.coverMediaId());
        int changed = jdbc.update("UPDATE course SET title=?,family_id=?,summary=?,body_markdown=?,duration_hours=?,"
                        + "modality=?,enrollment_status=?,cover_media_id=?,version=version+1,updated_at=now(),updated_by=? "
                        + "WHERE id=? AND version=?",
                request.title().trim(), request.familyId(), request.summary().trim(), request.bodyMarkdown(),
                request.durationHours(), request.modality(), request.enrollmentStatus(), request.coverMediaId(),
                actor.id(), id, request.version());
        if (changed != 1) throw versionConflict();
        replaceRequirements(id, request.requirements());
        return getCourse(id);
    }

    public Page<FamilyAdmin> listFamilies(int page, int size, Boolean active) {
        validatePage(page, size);
        String where = active == null ? "" : " WHERE active=?";
        List<Object> args = active == null ? List.of() : List.of(active);
        return page("SELECT id,slug,name,active,version FROM professional_family" + where
                        + " ORDER BY sort_order ASC,name ASC,id ASC",
                "SELECT COUNT(*) FROM professional_family" + where, args, page, size, this::family);
    }

    public FamilyAdmin getFamily(UUID id) {
        return one("SELECT id,slug,name,active,version FROM professional_family WHERE id=?", this::family, id);
    }

    @Transactional
    public FamilyAdmin createFamily(FamilyCreateRequest request) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO professional_family (id,slug,name,active,version) VALUES (?,?,?,TRUE,0)",
                    id, request.slug().trim(), request.name().trim());
        } catch (DuplicateKeyException exception) { throw slugConflict(); }
        return getFamily(id);
    }

    @Transactional
    public FamilyAdmin updateFamily(UUID id, FamilyUpdateRequest request) {
        FamilyAdmin family = getFamily(id); checkVersion(family.version(), request.version());
        if (!request.active() && jdbc.queryForObject(
                "SELECT COUNT(*) FROM course WHERE family_id=? AND status='PUBLISHED'", Long.class, id) > 0) {
            throw PublicApiException.conflict("FAMILY_HAS_PUBLISHED_COURSES",
                    "No se puede desactivar una familia con cursos publicados.");
        }
        int changed = jdbc.update("UPDATE professional_family SET name=?,active=?,version=version+1,updated_at=now() "
                        + "WHERE id=? AND version=?", request.name().trim(), request.active(), id, request.version());
        if (changed != 1) throw versionConflict();
        return getFamily(id);
    }

    public CoursePhotosResponse getCoursePhotos(UUID courseId) {
        CourseAdmin course = getCourse(courseId);
        List<CoursePhotoInput> photos = jdbc.query("SELECT id,media_id,alt,position,enabled,show_on_homepage FROM course_photo WHERE course_id=? ORDER BY position ASC,id ASC",
                (rs,row) -> new CoursePhotoInput(DatabaseIds.read(rs, "id"),DatabaseIds.read(rs, "media_id"),
                        rs.getString("alt"),rs.getInt("position"),rs.getBoolean("enabled"),rs.getBoolean("show_on_homepage")),courseId);
        return new CoursePhotosResponse(courseId,course.version(),photos);
    }

    @Transactional
    public CoursePhotosResponse replaceCoursePhotos(UUID courseId, CoursePhotosUpdateRequest request, AdminPrincipal actor) {
        CourseAdmin course=getCourse(courseId); checkVersion(course.version(),request.version());
        java.util.HashSet<Integer> positions=new java.util.HashSet<>();
        java.util.HashSet<UUID> ids=new java.util.HashSet<>();
        for(CoursePhotoInput photo:request.photos()) {
            if(!positions.add(photo.position())) throw PublicApiException.badRequest("photos","Cada posición debe ser única.");
            validateImage(photo.mediaId(),"photos.mediaId");
            if(photo.id()!=null) {
                if(!ids.add(photo.id())) throw PublicApiException.badRequest("photos","Cada id debe ser único.");
                Long owns=jdbc.queryForObject("SELECT COUNT(*) FROM course_photo WHERE id=? AND course_id=?",Long.class,photo.id(),courseId);
                if(owns==null||owns==0) throw PublicApiException.badRequest("photos","El id debe pertenecer a la galería del curso.");
            }
        }
        int changed=jdbc.update("UPDATE course SET version=version+1,updated_at=now(),updated_by=? WHERE id=? AND version=?",
                actor.id(),courseId,request.version());
        if(changed!=1) throw versionConflict();
        jdbc.update("DELETE FROM course_photo WHERE course_id=?",courseId);
        for(CoursePhotoInput photo:request.photos()) jdbc.update(
                "INSERT INTO course_photo (id,course_id,media_id,alt,position,enabled,show_on_homepage) VALUES (?,?,?,?,?,?,?)",
                photo.id()==null?UUID.randomUUID():photo.id(),courseId,photo.mediaId(),photo.alt().trim(),photo.position(),
                photo.enabled(),photo.showOnHomepage());
        return getCoursePhotos(courseId);
    }
    public Page<EventAdmin> listEvents(int page, int size, String status, String q) {
        validatePage(page,size); validateStatus(status);
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        addStatus(where,args,"e.status",status); addQuery(where,args,q,"e.title","e.description");
        return page("SELECT " + EVENT_COLUMNS + " FROM calendar_event e" + where
                        + " ORDER BY e.starts_at ASC,e.id ASC", "SELECT COUNT(*) FROM calendar_event e" + where,
                args,page,size,this::event);
    }

    public EventAdmin getEvent(UUID id) {
        return one("SELECT " + EVENT_COLUMNS + " FROM calendar_event e WHERE e.id=?", this::event, id);
    }

    @Transactional
    public EventAdmin createEvent(EventCreateRequest request, AdminPrincipal actor) {
        validateEvent(request.startsAt(),request.endsAt(),request.timezone(),request.allDay());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO calendar_event (id,slug,title,description,starts_at,ends_at,timezone,location,"
                            + "all_day,status,version,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?)",
                    id,request.slug().trim(),request.title().trim(),request.description(),request.startsAt(),
                    request.endsAt(),request.timezone(),request.location().trim(),request.allDay(),actor.id(),actor.id());
        } catch (DuplicateKeyException exception) { throw slugConflict(); }
        return getEvent(id);
    }

    @Transactional
    public EventAdmin updateEvent(UUID id, EventUpdateRequest request, AdminPrincipal actor) {
        EventAdmin current=getEvent(id); checkVersion(current.version(),request.version());
        validateEvent(request.startsAt(),request.endsAt(),request.timezone(),request.allDay());
        int changed=jdbc.update("UPDATE calendar_event SET title=?,description=?,starts_at=?,ends_at=?,timezone=?,"
                        + "location=?,all_day=?,version=version+1,updated_at=now(),updated_by=? WHERE id=? AND version=?",
                request.title().trim(),request.description(),request.startsAt(),request.endsAt(),request.timezone(),
                request.location().trim(),request.allDay(),actor.id(),id,request.version());
        if(changed!=1) throw versionConflict();
        return getEvent(id);
    }

    @Transactional
    public Object transition(String kind, UUID id, VersionRequest request, AdminPrincipal actor, boolean publish) {
        return switch (kind) {
            case "noticias" -> transitionOne("news", id, request.version(), actor, publish, this::getNews,
                    (value) -> { if (publish) validateOptionalImage(value.coverMediaId()); });
            case "cursos" -> transitionOne("course", id, request.version(), actor, publish, this::getCourse,
                    (value) -> { if (publish) { requireActiveFamily(value.familyId()); validateOptionalImage(value.coverMediaId()); }});
            case "eventos" -> transitionOne("calendar_event", id, request.version(), actor, publish, this::getEvent,
                    (value) -> { if (publish) validateEvent(value.startsAt(),value.endsAt(),value.timezone(),value.allDay()); });
            default -> throw new IllegalArgumentException("unsupported editorial type");
        };
    }

    private <T> T transitionOne(String table, UUID id, long version, AdminPrincipal actor, boolean publish,
                                java.util.function.Function<UUID,T> get,
                                java.util.function.Consumer<T> beforePublish) {
        T value=get.apply(id); long currentVersion=versionOf(value); String status=statusOf(value);
        checkVersion(currentVersion,version);
        if (publish) {
            if (!"DRAFT".equals(status)) throw PublicApiException.conflict("INVALID_STATE_TRANSITION",
                    "Sólo un borrador puede publicarse.");
            beforePublish.accept(value);
        } else if ("ARCHIVED".equals(status)) {
            throw PublicApiException.conflict("INVALID_STATE_TRANSITION", "ARCHIVED es terminal en v1.");
        }
        String next=publish?"PUBLISHED":"ARCHIVED";
        int changed=jdbc.update("UPDATE " + table + " SET status=?,published_at="
                        + (publish?"now()":"published_at") + ",version=version+1,updated_at=now(),updated_by=? "
                        + "WHERE id=? AND version=? AND status=?",next,actor.id(),id,version,status);
        if(changed!=1) throw versionConflict();
        return get.apply(id);
    }

    public InstitutionAdmin getInstitution() {
        List<InstitutionAdmin> rows=jdbc.query("SELECT name,description,address,email,phones,social_links,version "
                        + "FROM institution WHERE id=1",(rs,row)->institution(rs,row));
        if(rows.isEmpty()) throw PublicApiException.institutionNotConfigured();
        return rows.getFirst();
    }

    @Transactional
    public InstitutionAdmin updateInstitution(InstitutionUpdateRequest request, AdminPrincipal actor) {
        validateSocialLinks(request.socialLinks());
        String phones=json(request.phones()), links=json(request.socialLinks());
        List<Long> existing=jdbc.query("SELECT version FROM institution WHERE id=1",(rs,row)->rs.getLong(1));
        if(existing.isEmpty()) {
            if(request.version()!=0) throw versionConflict();
            try {
                jdbc.update("INSERT INTO institution (id,name,description,address,email,phones,social_links,version,updated_by) "
                                + "VALUES (1,?,?,?,?,?,?,0,?)",request.name().trim(),request.description(),
                        request.address().trim(),request.email().trim().toLowerCase(Locale.ROOT),phones,links,actor.id());
            } catch(DuplicateKeyException exception) { throw versionConflict(); }
        } else {
            checkVersion(existing.getFirst(),request.version());
            int changed=jdbc.update("UPDATE institution SET name=?,description=?,address=?,email=?,phones=?,social_links=?, "
                            + "version=version+1,updated_at=now(),updated_by=? WHERE id=1 AND version=?",
                    request.name().trim(),request.description(),request.address().trim(),
                    request.email().trim().toLowerCase(Locale.ROOT),phones,links,actor.id(),request.version());
            if(changed!=1) throw versionConflict();
        }
        return getInstitution();
    }

    private <T> Page<T> page(String select, String count, List<Object> filters, int page, int size, RowMapper<T> mapper) {
        long total=jdbc.queryForObject(count,Long.class,filters.toArray());
        List<Object> args=new ArrayList<>(filters); args.add(size); args.add((long)page*size);
        List<T> items=jdbc.query(select+" LIMIT ? OFFSET ?",mapper,args.toArray());
        return new Page<>(items,page,size,total,total==0?0:(total+size-1)/size);
    }

    private NewsAdmin news(ResultSet rs,int row) throws SQLException {
        return new NewsAdmin(DatabaseIds.read(rs, "id"),rs.getString("slug"),rs.getString("title"),
                rs.getString("summary"),rs.getString("body_markdown"),DatabaseIds.read(rs, "cover_media_id"),
                rs.getString("category"),rs.getBoolean("featured"),rs.getString("status"),argentina(rs,"published_at"),
                argentina(rs,"created_at"),argentina(rs,"updated_at"),rs.getLong("version"));
    }
    private CourseAdmin course(ResultSet rs,int row) throws SQLException {
        return new CourseAdmin(DatabaseIds.read(rs, "id"),rs.getString("slug"),rs.getString("title"),
                DatabaseIds.read(rs, "family_id"),rs.getString("summary"),rs.getString("body_markdown"),List.of(),
                rs.getObject("duration_hours",Integer.class),rs.getString("modality"),rs.getString("enrollment_status"),
                DatabaseIds.read(rs, "cover_media_id"),rs.getString("status"),argentina(rs,"published_at"),
                argentina(rs,"created_at"),argentina(rs,"updated_at"),rs.getLong("version"));
    }
    private EventAdmin event(ResultSet rs,int row) throws SQLException {
        return new EventAdmin(DatabaseIds.read(rs, "id"),rs.getString("slug"),rs.getString("title"),
                rs.getString("description"),argentina(rs,"starts_at"),argentina(rs,"ends_at"),rs.getString("timezone"),
                rs.getString("location"),rs.getBoolean("all_day"),rs.getString("status"),argentina(rs,"published_at"),
                argentina(rs,"created_at"),argentina(rs,"updated_at"),rs.getLong("version"));
    }
    private FamilyAdmin family(ResultSet rs,int row) throws SQLException {
        return new FamilyAdmin(DatabaseIds.read(rs, "id"),rs.getString("slug"),rs.getString("name"),
                rs.getBoolean("active"),rs.getLong("version"));
    }
    private InstitutionAdmin institution(ResultSet rs,int row) throws SQLException {
        return new InstitutionAdmin(rs.getString("name"),rs.getString("description"),rs.getString("address"),
                rs.getString("email"),readJson(rs.getString("phones"),new TypeReference<>(){}),
                readJson(rs.getString("social_links"),new TypeReference<>(){}),rs.getLong("version"));
    }

    private <T> T one(String sql,RowMapper<T> mapper,Object... args) {
        List<T> items=jdbc.query(sql,mapper,args); if(items.isEmpty()) throw PublicApiException.notFound(); return items.getFirst();
    }
    private OffsetDateTime argentina(ResultSet rs,String column) throws SQLException {
        return DatabaseTimes.argentina(rs,column);
    }
    private void replaceRequirements(UUID id,List<String> requirements) {
        jdbc.update("DELETE FROM course_requirement WHERE course_id=?",id);
        for(int i=0;i<requirements.size();i++) jdbc.update(
                "INSERT INTO course_requirement (course_id,position,requirement) VALUES (?,?,?)",id,i,requirements.get(i).trim());
    }
    private void validateOptionalImage(UUID id) { if(id!=null) validateImage(id,"coverMediaId"); }
    private void validateImage(UUID id,String field) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM media WHERE id=? AND enabled=TRUE AND visibility='PUBLIC' "
                + "AND mime_type IN ('image/jpeg','image/png','image/webp')",Long.class,id);
        if(count==null||count==0) throw PublicApiException.badRequest(field,"Debe ser una imagen pública habilitada.");
    }    private void requireFamily(UUID id) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM professional_family WHERE id=?",Long.class,id);
        if(count==null||count==0) throw PublicApiException.badRequest("familyId","La familia no existe.");
    }
    private void requireActiveFamily(UUID id) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM professional_family WHERE id=? AND active=TRUE",Long.class,id);
        if(count==null||count==0) throw PublicApiException.conflict("FAMILY_INACTIVE","El curso requiere una familia activa para publicarse.");
    }
    private void validateMarkdown(String value,String field) {
        if(value!=null&&value.matches("(?is).*<\\s*/?\\s*[a-z!][^>]*>.*"))
            throw PublicApiException.badRequest(field,"Usá Markdown sin HTML crudo.");
    }
    private void validateEvent(OffsetDateTime starts,OffsetDateTime ends,String timezone,boolean allDay) {
        if(!"America/Argentina/Buenos_Aires".equals(timezone)) throw PublicApiException.badRequest("timezone","La zona debe ser America/Argentina/Buenos_Aires.");
        if(!ends.isAfter(starts)) throw PublicApiException.badRequest("endsAt","Debe ser posterior a startsAt.");
        if(!offsetMatchesArgentina(starts)||!offsetMatchesArgentina(ends)) throw PublicApiException.badRequest("startsAt","El offset no coincide con la zona horaria de Argentina.");
        if(allDay&&(starts.toLocalTime().toSecondOfDay()!=0||starts.getNano()!=0||ends.toLocalTime().toSecondOfDay()!=0||ends.getNano()!=0))
            throw PublicApiException.badRequest("allDay","Un evento de día completo usa medianoche local y fin exclusivo.");
    }
    private boolean offsetMatchesArgentina(OffsetDateTime value) {
        ZoneOffset expected=ARGENTINA.getRules().getOffset(value.toInstant()); return expected.equals(value.getOffset());
    }
    private void validateSocialLinks(List<SocialLinkInput> links) {
        for(int i=0;i<links.size();i++) {
            try { URI uri=URI.create(links.get(i).url()); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)
                throw new IllegalArgumentException(); }
            catch(IllegalArgumentException ex) { throw PublicApiException.badRequest("socialLinks["+i+"].url","La URL debe usar HTTPS."); }
        }
    }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);} }
    private <T> T readJson(String json,TypeReference<T> type) { try{return mapper.readValue(json,type);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);} }
    private void validatePage(int page,int size) {
        if(page<0) throw PublicApiException.badRequest("page","Debe ser mayor o igual a cero.");
        if(size<1||size>100) throw PublicApiException.badRequest("size","Debe estar entre 1 y 100.");
    }
    private void validateStatus(String status) {
        if(status!=null&&!status.isBlank()&&!List.of("DRAFT","PUBLISHED","ARCHIVED").contains(status))
            throw PublicApiException.badRequest("status","El estado editorial no es válido.");
    }
    private void addStatus(StringBuilder where,List<Object> args,String column,String status) {
        if(status!=null&&!status.isBlank()){where.append(" AND ").append(column).append("=?");args.add(status);}
    }
    private void addQuery(StringBuilder where,List<Object> args,String q,String first,String second) {
        if(q!=null&&!q.isBlank()) { validateFilter(q,"q",120); where.append(" AND (LOWER(").append(first).append(") LIKE LOWER(?) OR LOWER(").append(second).append(") LIKE LOWER(?))"); String v="%"+q.trim()+"%"; args.add(v); args.add(v); }
    }
    private void validateFilter(String value,String name,int max) {
        if(value.length()>max) throw PublicApiException.badRequest(name,"Supera el máximo de caracteres permitido.");
    }    private void checkVersion(long current,long supplied) { if(current!=supplied) throw versionConflict(); }
    private PublicApiException versionConflict() { return PublicApiException.conflict("VERSION_CONFLICT","El recurso cambió desde que lo consultaste. Recargalo antes de guardar."); }
    private PublicApiException slugConflict() { return PublicApiException.conflict("SLUG_EXISTS","Ya existe un recurso con ese slug."); }
    private long versionOf(Object value) { if(value instanceof NewsAdmin x)return x.version();if(value instanceof CourseAdmin x)return x.version();return ((EventAdmin)value).version(); }
    private String statusOf(Object value) { if(value instanceof NewsAdmin x)return x.status();if(value instanceof CourseAdmin x)return x.status();return ((EventAdmin)value).status(); }
}











