CREATE TABLE admin_user_role (
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR')),
    PRIMARY KEY (user_id, role),
    CONSTRAINT admin_user_role_user_fk FOREIGN KEY (user_id) REFERENCES admin_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO admin_user_role (user_id, role)
SELECT id, role FROM admin_user;

CREATE INDEX admin_user_role_lookup_idx ON admin_user_role (role, user_id);

ALTER TABLE admin_user DROP COLUMN role;
