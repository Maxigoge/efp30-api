CREATE TABLE admin_user_role (
    user_id UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR')),
    PRIMARY KEY (user_id, role)
);

INSERT INTO admin_user_role (user_id, role)
SELECT id, role FROM admin_user;

CREATE INDEX admin_user_role_lookup_idx ON admin_user_role (role, user_id);

ALTER TABLE admin_user DROP COLUMN role;
