CREATE TABLE media (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    object_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(64) NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHECK ((mime_type = 'application/pdf' AND size_bytes <= 10485760)
        OR (mime_type <> 'application/pdf' AND size_bytes <= 5242880)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_user (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'EDITOR' CHECK (role IN ('ADMIN', 'EDITOR')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE professional_family (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE news (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(400) NOT NULL,
    body_markdown MEDIUMTEXT NOT NULL,
    category VARCHAR(60) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    cover_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT news_cover_media_fk FOREIGN KEY (cover_media_id) REFERENCES media(id) ON DELETE SET NULL,
    CONSTRAINT news_created_by_fk FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CONSTRAINT news_updated_by_fk FOREIGN KEY (updated_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX news_public_order_idx ON news (status, published_at DESC, id);
CREATE INDEX news_editorial_status_idx ON news (status, updated_at DESC);

CREATE TABLE course (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    summary VARCHAR(400) NOT NULL,
    body_markdown MEDIUMTEXT NOT NULL,
    duration_hours INTEGER CHECK (duration_hours BETWEEN 1 AND 10000),
    modality VARCHAR(16) NOT NULL CHECK (modality IN ('PRESENTIAL', 'ONLINE', 'HYBRID')),
    enrollment_status VARCHAR(16) NOT NULL CHECK (enrollment_status IN ('CONSULTATION', 'OPEN', 'CLOSED')),
    cover_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT course_family_fk FOREIGN KEY (family_id) REFERENCES professional_family(id) ON DELETE RESTRICT,
    CONSTRAINT course_cover_media_fk FOREIGN KEY (cover_media_id) REFERENCES media(id) ON DELETE SET NULL,
    CONSTRAINT course_created_by_fk FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CONSTRAINT course_updated_by_fk FOREIGN KEY (updated_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX course_public_order_idx ON course (status, family_id, title, id);
CREATE INDEX course_editorial_status_idx ON course (status, updated_at DESC);

CREATE TABLE course_requirement (
    course_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `position` SMALLINT NOT NULL CHECK (`position` BETWEEN 0 AND 49),
    requirement VARCHAR(240) NOT NULL CHECK (CHAR_LENGTH(requirement) > 0),
    PRIMARY KEY (course_id, `position`),
    CONSTRAINT course_requirement_course_fk FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE course_photo (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    course_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    alt VARCHAR(240) NOT NULL CHECK (CHAR_LENGTH(alt) > 0),
    `position` SMALLINT NOT NULL CHECK (`position` BETWEEN 0 AND 99),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    show_on_homepage BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (course_id, `position`),
    CONSTRAINT course_photo_course_fk FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT course_photo_media_fk FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX course_photo_homepage_idx ON course_photo (enabled, show_on_homepage, `position`, course_id, id);

CREATE TABLE calendar_event (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    description MEDIUMTEXT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires'
        CHECK (timezone = 'America/Argentina/Buenos_Aires'),
    location VARCHAR(240) NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT event_created_by_fk FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CONSTRAINT event_updated_by_fk FOREIGN KEY (updated_by) REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK (ends_at > starts_at),
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX calendar_event_public_range_idx ON calendar_event (status, starts_at, ends_at, id);
CREATE INDEX calendar_event_editorial_status_idx ON calendar_event (status, starts_at);

CREATE TABLE institution (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    address VARCHAR(240) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL,
    phones JSON NOT NULL,
    social_links JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT institution_updated_by_fk FOREIGN KEY (updated_by) REFERENCES admin_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INTEGER NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID),
    UNIQUE INDEX SPRING_SESSION_IX1 (SESSION_ID),
    INDEX SPRING_SESSION_IX2 (EXPIRY_TIME),
    INDEX SPRING_SESSION_IX3 (PRINCIPAL_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
