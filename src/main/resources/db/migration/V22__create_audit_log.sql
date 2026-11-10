-- Business audit log (roadmap 8.1, owner decision 2026-11-07): who did what, to which record,
-- and when. Rows are written in the same transaction as the action they describe, so an action
-- and its log entry are saved together or not at all.
--
-- Append-only: the triggers below reject UPDATE, DELETE and TRUNCATE, so entries can't be
-- changed or removed through the application. No foreign keys, so entries outlive the records
-- they mention.
CREATE TABLE audit_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Staff code (e.g. 900100) of the person who acted; NULL for automated system actions.
    actor_staff_id VARCHAR(50),
    action         VARCHAR(60)  NOT NULL,
    target_type    VARCHAR(40)  NOT NULL,
    target_id      VARCHAR(100) NOT NULL,
    -- Human-readable summary. Never passwords, reset codes or document contents.
    details        TEXT
);

CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id, occurred_at);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_staff_id, occurred_at);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);

CREATE FUNCTION audit_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
