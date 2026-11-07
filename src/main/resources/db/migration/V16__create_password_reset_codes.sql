-- Staff self-service password reset (roadmap 1.5c). At most one pending code per staff member.
-- Only a BCrypt hash of the code is stored. A row is deleted when the code is used, expires,
-- or reaches the wrong-attempt limit.
CREATE TABLE password_reset_codes (
    id              UUID PRIMARY KEY,
    staff_id        UUID NOT NULL UNIQUE,
    code_hash       VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_password_reset_codes_staff FOREIGN KEY (staff_id) REFERENCES staffs (id) ON DELETE CASCADE
);
