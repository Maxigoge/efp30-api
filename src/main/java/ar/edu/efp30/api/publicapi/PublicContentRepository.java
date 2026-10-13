package ar.edu.efp30.api.publicapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ar.edu.efp30.api.publicapi.PublicContentDtos.*;

@Repository
public class PublicContentRepository {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String PUBLIC_IMAGE = "m.mime_type IN ('image/jpeg', 'image/png', 'image/webp')";
    private static final String COVER_URL = "CASE WHEN m.id IS NOT NULL AND m.enabled AND m.visibility = 'PUBLIC' "
            + "AND " + PUBLIC_IMAGE + " THEN CONCAT('/api/v1/public/media/', m.id) ELSE NULL END";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PublicContentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Page<NewsSummary> listNews(int page, int size, String query, String category, Boolean featured) {
        StringBuilder where = new StringBuilder(" WHERE n.status = 'PUBLISHED'");
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" AND (n.title ILIKE ? ESCAPE '!' OR n.summary ILIKE ? ESCAPE '!')");
            String pattern = "%" + escapeLike(query.trim()) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND n.category = ?");
            args.add(category.trim());
        }
        if (featured != null) {
            where.append(" AND n.featured = ?");
            args.add(featured);
        }
        String from = " FROM news n LEFT JOIN media m ON m.id = n.cover_media_id";
        String select = "SELECT n.id, n.slug, n.title, n.summary, " + COVER_URL + " AS cover_url, "
                + "n.category, n.featured, n.published_at" + from + where
                + " ORDER BY n.published_at DESC, n.id ASC LIMIT ? OFFSET ?";
        return page(select, "SELECT COUNT(*)" + from + where, args, page, size, this::newsSummary);
    }

    public News getNews(String slug) {
        String sql = "SELECT n.id, n.slug, n.title, n.summary, n.body_markdown, " + COVER_URL + " AS cover_url, "
                + "n.category, n.featured, n.published_at FROM news n "
                + "LEFT JOIN media m ON m.id = n.cover_media_id "
                + "WHERE n.slug = ? AND n.status = 'PUBLISHED'";
        return findOne(sql, (rs, row) -> new News(rs.getObject("id", UUID.class), rs.getString("slug"),
                rs.getString("title"), rs.getString("summary"), rs.getString("body_markdown"),
                rs.getString("cover_url"), rs.getString("category"), rs.getBoolean("featured"),
                argentina(rs, "published_at")), slug);
    }

    public Page<CourseSummary> listCourses(int page, int size, String query, UUID familyId) {
        StringBuilder where = new StringBuilder(" WHERE c.status = 'PUBLISHED' AND f.active = TRUE");
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" AND (c.title ILIKE ? ESCAPE '!' OR c.summary ILIKE ? ESCAPE '!')");
            String pattern = "%" + escapeLike(query.trim()) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (familyId != null) {
            where.append(" AND c.family_id = ?");
            args.add(familyId);
        }
        String from = " FROM course c JOIN professional_family f ON f.id = c.family_id "
                + "LEFT JOIN media m ON m.id = c.cover_media_id";
        String columns = "c.id, c.slug, c.title, f.id AS family_id, f.slug AS family_slug, "
                + "f.name AS family_name, c.summary, c.duration_hours, c.modality, c.enrollment_status, "
                + COVER_URL + " AS cover_url";
        String select = "SELECT " + columns + from + where + " ORDER BY c.title ASC, c.id ASC LIMIT ? OFFSET ?";
        return page(select, "SELECT COUNT(*)" + from + where, args, page, size, this::courseSummary);
    }

    public Course getCourse(String slug) {
        String from = " FROM course c JOIN professional_family f ON f.id = c.family_id "
                + "LEFT JOIN media m ON m.id = c.cover_media_id";
        String sql = "SELECT c.id, c.slug, c.title, f.id AS family_id, f.slug AS family_slug, "
                + "f.name AS family_name, c.summary, c.body_markdown, c.duration_hours, c.modality, "
                + "c.enrollment_status, " + COVER_URL + " AS cover_url" + from
                + " WHERE c.slug = ? AND c.status = 'PUBLISHED' AND f.active = TRUE";
        CourseDetailRow row = findOne(sql, (rs, index) -> new CourseDetailRow(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                new FamilyReference(rs.getObject("family_id", UUID.class), rs.getString("family_slug"),
                        rs.getString("family_name")),
                rs.getString("summary"), rs.getString("body_markdown"),
                rs.getObject("duration_hours", Integer.class), rs.getString("modality"),
                rs.getString("enrollment_status"), rs.getString("cover_url")), slug);
        List<String> requirements = jdbc.query(
                "SELECT requirement FROM course_requirement WHERE course_id = ? ORDER BY position ASC",
                (rs, index) -> rs.getString("requirement"), row.id());
        List<CoursePhoto> photos = jdbc.query("""
                        SELECT cp.id, cp.alt, cp.position, m.id AS media_id
                        FROM course_photo cp
                        JOIN media m ON m.id = cp.media_id
                        WHERE cp.course_id = ? AND cp.enabled = TRUE
                          AND m.enabled = TRUE AND m.visibility = 'PUBLIC'
                          AND m.mime_type IN ('image/jpeg', 'image/png', 'image/webp')
                        ORDER BY cp.position ASC, cp.id ASC
                        """,
                (rs, index) -> new CoursePhoto(rs.getObject("id", UUID.class),
                        mediaUrl(rs.getObject("media_id", UUID.class)), rs.getString("alt"), rs.getInt("position")),
                row.id());
        return new Course(row.id(), row.slug(), row.title(), row.family(), row.summary(), row.bodyMarkdown(),
                requirements, row.durationHours(), row.modality(), row.enrollmentStatus(), row.coverUrl(), photos);
    }

    public FamilyList listFamilies() {
        List<Family> items = jdbc.query("""
                        SELECT f.id, f.slug, f.name
                        FROM professional_family f
                        WHERE f.active = TRUE AND EXISTS (
                            SELECT 1 FROM course c WHERE c.family_id = f.id AND c.status = 'PUBLISHED'
                        )
                        ORDER BY f.sort_order ASC, f.name ASC, f.id ASC
                        """,
                (rs, row) -> new Family(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name")));
        return new FamilyList(items);
    }

    public Page<Event> listEvents(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        String fromSql = " FROM calendar_event e WHERE e.status = 'PUBLISHED' AND e.starts_at < ? AND e.ends_at > ?";
        String select = "SELECT e.id, e.slug, e.title, e.description, e.starts_at, e.ends_at, e.timezone, "
                + "e.location, e.all_day" + fromSql + " ORDER BY e.starts_at ASC, e.id ASC LIMIT ? OFFSET ?";
        return page(select, "SELECT COUNT(*)" + fromSql, List.of(to, from), page, size, this::event);
    }

    public Event getEvent(String slug) {
        String sql = "SELECT e.id, e.slug, e.title, e.description, e.starts_at, e.ends_at, e.timezone, "
                + "e.location, e.all_day FROM calendar_event e WHERE e.slug = ? AND e.status = 'PUBLISHED'";
        return findOne(sql, this::event, slug);
    }

    public Carousel carousel() {
        List<CarouselItem> items = jdbc.query("""
                        SELECT cp.id, c.id AS course_id, c.slug AS course_slug, c.title AS course_title,
                               m.id AS media_id, cp.alt, cp.position
                        FROM course_photo cp
                        JOIN course c ON c.id = cp.course_id AND c.status = 'PUBLISHED'
                        JOIN media m ON m.id = cp.media_id AND m.enabled = TRUE AND m.visibility = 'PUBLIC'
                        WHERE cp.enabled = TRUE AND cp.show_on_homepage = TRUE
                          AND m.mime_type IN ('image/jpeg', 'image/png', 'image/webp')
                        ORDER BY cp.position ASC, c.id ASC, cp.id ASC
                        """,
                (rs, row) -> {
                    UUID mediaId = rs.getObject("media_id", UUID.class);
                    return new CarouselItem(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                            rs.getString("course_slug"), rs.getString("course_title"), mediaId,
                            mediaUrl(mediaId), rs.getString("alt"), rs.getInt("position"));
                });
        return new Carousel(items);
    }

    public Institution institution() {
        List<Institution> rows = jdbc.query("""
                        SELECT name, description, address, email, phones, social_links
                        FROM institution WHERE id = 1
                        """,
                (rs, row) -> new Institution(rs.getString("name"), rs.getString("description"),
                        rs.getString("address"), rs.getString("email"), readPhones(rs.getString("phones")),
                        readSocialLinks(rs.getString("social_links"))));
        if (rows.isEmpty()) {
            throw PublicApiException.institutionNotConfigured();
        }
        return rows.getFirst();
    }

    public MediaFile getPublicMedia(UUID id) {
        return findOne("""
                        SELECT id, object_key, original_name, mime_type, size_bytes
                        FROM media
                        WHERE id = ? AND visibility = 'PUBLIC' AND enabled = TRUE
                        """,
                (rs, row) -> new MediaFile(rs.getObject("id", UUID.class), rs.getString("object_key"),
                        rs.getString("original_name"), rs.getString("mime_type"), rs.getLong("size_bytes")), id);
    }

    private <T> Page<T> page(String select, String count, List<Object> filterArgs, int page, int size,
                             RowMapper<T> mapper) {
        long totalItems = jdbc.queryForObject(count, Long.class, filterArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(filterArgs);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<T> items = jdbc.query(select, mapper, pageArgs.toArray());
        long totalPages = totalItems == 0 ? 0 : (totalItems + size - 1) / size;
        return new Page<>(items, page, size, totalItems, totalPages);
    }

    private NewsSummary newsSummary(ResultSet rs, int row) throws SQLException {
        return new NewsSummary(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("cover_url"), rs.getString("category"),
                rs.getBoolean("featured"), argentina(rs, "published_at"));
    }

    private CourseSummary courseSummary(ResultSet rs, int row) throws SQLException {
        return new CourseSummary(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                new FamilyReference(rs.getObject("family_id", UUID.class), rs.getString("family_slug"),
                        rs.getString("family_name")),
                rs.getString("summary"), rs.getObject("duration_hours", Integer.class), rs.getString("modality"),
                rs.getString("enrollment_status"), rs.getString("cover_url"));
    }

    private Event event(ResultSet rs, int row) throws SQLException {
        return new Event(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("description"), argentina(rs, "starts_at"), argentina(rs, "ends_at"),
                rs.getString("timezone"), rs.getString("location"), rs.getBoolean("all_day"));
    }

    private OffsetDateTime argentina(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value.atZoneSameInstant(ARGENTINA).toOffsetDateTime();
    }

    private <T> T findOne(String sql, RowMapper<T> mapper, Object... args) {
        List<T> items = jdbc.query(sql, mapper, args);
        if (items.isEmpty()) {
            throw PublicApiException.notFound();
        }
        return items.getFirst();
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String mediaUrl(UUID id) {
        return "/api/v1/public/media/" + id;
    }

    private List<String> readPhones(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    private List<SocialLink> readSocialLinks(String json) {
        return readJson(json, new TypeReference<>() {
        });
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored public JSON data is invalid", exception);
        }
    }

    private record CourseDetailRow(UUID id, String slug, String title, FamilyReference family, String summary,
                                   String bodyMarkdown, Integer durationHours, String modality,
                                   String enrollmentStatus, String coverUrl) {
    }
}
