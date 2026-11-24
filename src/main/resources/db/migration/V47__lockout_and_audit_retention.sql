-- Owner decisions 2026-11-24 (increment #14).

-- Login lockout: 5 wrong passwords in a row lock a staff or collector account until an
-- administrator unlocks it. Written only by bulk updates (like sessions_valid_after), so a staff
-- save elsewhere can't overwrite a count taken in between.
ALTER TABLE staffs
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    ADD COLUMN locked_at             TIMESTAMP;

-- Audit log entries are kept for 7 years. The log stays append-only: nothing can be changed,
-- and the only rows that can be deleted are those older than 7 years (the retention job).
CREATE OR REPLACE FUNCTION audit_log_reject_change() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.occurred_at < now() - INTERVAL '7 years' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

-- Reading the log by action (the other filters already have indexes, V22).
CREATE INDEX idx_audit_log_action ON audit_log (action, occurred_at);
