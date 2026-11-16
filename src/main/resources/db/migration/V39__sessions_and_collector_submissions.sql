-- Sessions (owner decisions 2026-11-15): refresh tokens are kept on the server so they can be
-- revoked, and rotated on every refresh. Only a SHA-256 hash of each token is stored.
--   family_id:   every token from one login shares it; revoking a session revokes the family
--   parent_id:   the token this one replaced
--   replaced_at: when this token was swapped for a new one (it can't be used again after that)
CREATE TABLE refresh_token (
    id            UUID PRIMARY KEY,
    staff_id      UUID        NOT NULL REFERENCES staffs (id),
    family_id     UUID        NOT NULL,
    parent_id     UUID REFERENCES refresh_token (id) ON DELETE SET NULL,
    token_hash    CHAR(64)    NOT NULL UNIQUE,
    issued_at     TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    replaced_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(30) CHECK (revoke_reason IN ('LOGOUT', 'REUSE_DETECTED', 'SUPERSEDED', 'DAILY_APPROVAL',
                                                       'PASSWORD_RESET', 'DEACTIVATED', 'ROLES_CHANGED')),
    CONSTRAINT chk_refresh_token_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX idx_refresh_token_staff_active ON refresh_token (staff_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_parent ON refresh_token (parent_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);

-- Access tokens issued before this moment are refused (logout, daily approval, password reset,
-- deactivation, role change), so ending a session takes effect at once rather than when the
-- access token expires.
ALTER TABLE staffs
    ADD COLUMN sessions_valid_after TIMESTAMPTZ;

-- Collector daily submissions (owner decisions 2026-11-15). The collector submits to close the
-- day; the submission covers every collector payment not yet in a submission (COMPLETED ones
-- make up the expected cash; FAILED ones are listed for the approver but not expected). The
-- branch's agent or an administrator approves with the cash counted; a shortage stays in 1015,
-- a surplus goes to 2030.
CREATE TABLE collector_submission (
    id                      UUID PRIMARY KEY,
    collector_id            UUID           NOT NULL REFERENCES staffs (id),
    branch_id               UUID           NOT NULL REFERENCES branch_table (id),
    status                  VARCHAR(20)    NOT NULL CHECK (status IN ('SUBMITTED', 'APPROVED')),
    submitted_at            TIMESTAMP      NOT NULL,
    payment_count           INT            NOT NULL CHECK (payment_count >= 0),
    expected_amount         DECIMAL(19, 4) NOT NULL CHECK (expected_amount >= 0),
    failed_count            INT            NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    failed_amount           DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (failed_amount >= 0),
    counted_amount          DECIMAL(19, 4) CHECK (counted_amount >= 0),
    shortage_amount         DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (shortage_amount >= 0),
    overage_amount          DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (overage_amount >= 0),
    approved_by             UUID REFERENCES staffs (id),
    approved_at             TIMESTAMP,
    handover_transaction_id UUID REFERENCES transaction (id),
    CONSTRAINT chk_collector_submission_approved CHECK (
        status <> 'APPROVED' OR (counted_amount IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

-- At most one submission waiting for approval per collector.
CREATE UNIQUE INDEX uq_collector_submission_open ON collector_submission (collector_id) WHERE status = 'SUBMITTED';
CREATE INDEX idx_collector_submission_branch ON collector_submission (branch_id, status, submitted_at);
CREATE INDEX idx_collector_submission_collector ON collector_submission (collector_id, submitted_at);

-- An approved submission is final, like a posted transaction.
CREATE FUNCTION reject_approved_submission_change() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'APPROVED' THEN
        RAISE EXCEPTION 'Approved collector submissions cannot be changed or deleted';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_collector_submission_final
    BEFORE UPDATE OR DELETE ON collector_submission
    FOR EACH ROW EXECUTE FUNCTION reject_approved_submission_change();

ALTER TABLE transaction
    ADD COLUMN collector_submission_id UUID REFERENCES collector_submission (id);

CREATE INDEX idx_transaction_collector_submission ON transaction (collector_submission_id);
CREATE INDEX idx_transaction_unsubmitted_collector ON transaction (initiated_by, status)
    WHERE channel = 'COLLECTOR' AND collector_submission_id IS NULL;

-- Ledger for the cash handover. 2030 is new (owner decision 2026-11-15: surplus to a suspense
-- liability; a shortage stays in 1015). Seeded as data, for the accountant to review.
INSERT INTO gl_account (account_code, name, category, is_header)
VALUES ('2030', 'Cash Overages (Suspense)', 'LIABILITY', FALSE);

-- Amount parts for the handover:
--   PRINCIPAL: the cash counted
--   SETTLED:   the part of it that settles recorded collections (at most the expected amount)
--   OVERAGE:   cash counted above the expected amount
ALTER TABLE posting_rule DROP CONSTRAINT posting_rule_amount_part_check;
ALTER TABLE posting_rule
    ADD CONSTRAINT posting_rule_amount_part_check
        CHECK (amount_part IN ('PRINCIPAL', 'FEE', 'SHARE', 'REMAINDER', 'SETTLED', 'OVERAGE'));

INSERT INTO posting_rule (event, amount_part, entry_type, gl_account_id, match_side, match_account_type, description)
SELECT r.event, r.amount_part, r.entry_type, g.id, NULL, NULL, r.description
FROM (VALUES
    ('COLLECTOR_HANDOVER', 'PRINCIPAL', 'DEBIT',  '1010', 'Cash counted at the office'),
    ('COLLECTOR_HANDOVER', 'SETTLED',   'CREDIT', '1015', 'Recorded collections settled by the handover; a shortage stays in 1015'),
    ('COLLECTOR_HANDOVER', 'OVERAGE',   'CREDIT', '2030', 'Cash counted above the recorded collections, held in suspense')
) AS r (event, amount_part, entry_type, account_code, description)
JOIN gl_account g ON g.account_code = r.account_code;
