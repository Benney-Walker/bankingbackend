-- Member update requests (owner decisions 2026-11-23, increment #5). Staff propose a change to a
-- member's phone number, address or occupation (the only fields that can change, owner rule
-- 2026-11-07); an administrator approves (the change is applied) or rejects it with a reason.
-- Administrators can still update a member directly. At most one waiting request per member.
CREATE TABLE member_update_request (
    id                   UUID PRIMARY KEY,
    member_id            UUID         NOT NULL REFERENCES member (id),
    requested_by         UUID         NOT NULL REFERENCES staffs (id),
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    phone_number         VARCHAR(30)  NOT NULL,
    residential_address  TEXT         NOT NULL,
    occupation           VARCHAR(150) NOT NULL,
    requested_at         TIMESTAMP    NOT NULL,
    reviewed_by          UUID REFERENCES staffs (id),
    reviewed_at          TIMESTAMP,
    rejection_reason     TEXT,
    CONSTRAINT chk_member_update_reviewed CHECK (
        status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT chk_member_update_rejection_reason CHECK (
        status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND btrim(rejection_reason) <> ''))
);

CREATE UNIQUE INDEX uq_member_update_request_pending ON member_update_request (member_id) WHERE status = 'PENDING';
CREATE INDEX idx_member_update_request_status ON member_update_request (status, requested_at);
CREATE INDEX idx_member_update_request_requested_by ON member_update_request (requested_by, requested_at);

-- A reviewed request is final.
CREATE FUNCTION reject_reviewed_member_update_change() RETURNS trigger AS $$
BEGIN
    IF OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Reviewed member update requests cannot be changed or deleted';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_update_request_final
    BEFORE UPDATE OR DELETE ON member_update_request
    FOR EACH ROW EXECUTE FUNCTION reject_reviewed_member_update_change();
