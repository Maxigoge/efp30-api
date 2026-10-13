CREATE TABLE media (
    id UUID PRIMARY KEY,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(64) NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    sha256 CHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((mime_type = 'application/pdf' AND size_bytes <= 10485760)
        OR (mime_type <> 'application/pdf' AND size_bytes <= 5242880))
);

CREATE TABLE admin_user (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX admin_user_email_lower_uq ON admin_user (lower(email));

CREATE TABLE professional_family (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE news (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(400) NOT NULL,
    body_markdown TEXT NOT NULL CHECK (length(body_markdown) BETWEEN 1 AND 50000),
    category VARCHAR(60) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    cover_media_id UUID REFERENCES media(id) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
);
CREATE INDEX news_public_order_idx ON news (published_at DESC, id) WHERE status = 'PUBLISHED';
CREATE INDEX news_editorial_status_idx ON news (status, updated_at DESC);

CREATE TABLE course (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title VARCHAR(180) NOT NULL,
    family_id UUID NOT NULL REFERENCES professional_family(id) ON DELETE RESTRICT,
    summary VARCHAR(400) NOT NULL,
    body_markdown TEXT NOT NULL CHECK (length(body_markdown) BETWEEN 1 AND 50000),
    duration_hours INTEGER CHECK (duration_hours BETWEEN 1 AND 10000),
    modality VARCHAR(16) NOT NULL CHECK (modality IN ('ON_SITE', 'DISTANCE', 'HYBRID')),
    enrollment_status VARCHAR(16) NOT NULL CHECK (enrollment_status IN ('CONSULTATION', 'OPEN', 'CLOSED')),
    cover_media_id UUID REFERENCES media(id) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
);
CREATE INDEX course_public_order_idx ON course (family_id, title, id) WHERE status = 'PUBLISHED';
CREATE INDEX course_editorial_status_idx ON course (status, updated_at DESC);

CREATE TABLE course_requirement (
    course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL CHECK (position BETWEEN 0 AND 49),
    requirement VARCHAR(240) NOT NULL CHECK (length(requirement) > 0),
    PRIMARY KEY (course_id, position)
);

CREATE TABLE course_photo (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE RESTRICT,
    alt VARCHAR(240) NOT NULL CHECK (length(alt) > 0),
    position SMALLINT NOT NULL CHECK (position BETWEEN 0 AND 99),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    show_on_homepage BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (course_id, position)
);
CREATE INDEX course_photo_homepage_idx ON course_photo (position, course_id, id) WHERE enabled AND show_on_homepage;

CREATE TABLE calendar_event (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title VARCHAR(180) NOT NULL,
    description VARCHAR(20000) NOT NULL CHECK (length(description) > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires'
        CHECK (timezone = 'America/Argentina/Buenos_Aires'),
    location VARCHAR(240) NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES admin_user(id) ON DELETE SET NULL,
    CHECK (ends_at > starts_at),
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED'),
    CHECK (NOT all_day OR (
        starts_at = date_trunc('day', starts_at AT TIME ZONE timezone) AT TIME ZONE timezone
        AND ends_at = date_trunc('day', ends_at AT TIME ZONE timezone) AT TIME ZONE timezone
    ))
);
CREATE INDEX calendar_event_public_range_idx ON calendar_event (starts_at, ends_at, id) WHERE status = 'PUBLISHED';
CREATE INDEX calendar_event_editorial_status_idx ON calendar_event (status, starts_at);

CREATE TABLE institution (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    name VARCHAR(180) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    address VARCHAR(240) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL,
    phones JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(phones) = 'array'),
    social_links JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(social_links) = 'array'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES admin_user(id) ON DELETE SET NULL
);

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INTEGER NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
