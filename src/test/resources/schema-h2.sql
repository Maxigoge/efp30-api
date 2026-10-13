DROP ALL OBJECTS;

CREATE TABLE media (
    id UUID PRIMARY KEY,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE professional_family (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE news (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(400) NOT NULL,
    body_markdown VARCHAR(50000) NOT NULL,
    category VARCHAR(60) NOT NULL,
    featured BOOLEAN NOT NULL,
    cover_media_id UUID,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE course (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(180) NOT NULL,
    family_id UUID NOT NULL,
    summary VARCHAR(400) NOT NULL,
    body_markdown VARCHAR(50000) NOT NULL,
    duration_hours INTEGER,
    modality VARCHAR(16) NOT NULL,
    enrollment_status VARCHAR(16) NOT NULL,
    cover_media_id UUID,
    status VARCHAR(16) NOT NULL
);

CREATE TABLE course_requirement (
    course_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    requirement VARCHAR(240) NOT NULL,
    PRIMARY KEY (course_id, position)
);

CREATE TABLE course_photo (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    media_id UUID NOT NULL,
    alt VARCHAR(240) NOT NULL,
    position SMALLINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    show_on_homepage BOOLEAN NOT NULL,
    UNIQUE (course_id, position)
);

CREATE TABLE calendar_event (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(20000) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    location VARCHAR(240) NOT NULL,
    all_day BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL
);

CREATE TABLE institution (
    id SMALLINT PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    address VARCHAR(240) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phones VARCHAR(4000) NOT NULL,
    social_links VARCHAR(4000) NOT NULL
);

CREATE TABLE admin_user (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_user_role (
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id, role)
);
