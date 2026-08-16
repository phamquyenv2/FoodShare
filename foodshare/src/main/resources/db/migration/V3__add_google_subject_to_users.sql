ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255) NULL AFTER email,
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject);
