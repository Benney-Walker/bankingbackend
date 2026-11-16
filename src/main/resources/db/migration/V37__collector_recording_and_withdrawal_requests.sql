-- Collector recording (roadmap 6.x, owner decisions 2026-11-07 and 2026-11-14).

-- How a transaction was recorded. Collector payments start PENDING and are applied (balance,
-- ledger, SMS) when their 10-minute correction window closes. recorded_at is the device time
-- of a payment recorded offline; offline records arrive after their window and are applied on
-- arrival. failure_reason explains a collector payment that couldn't be applied.
ALTER TABLE transaction
    ADD COLUMN channel        VARCHAR(20) NOT NULL DEFAULT 'OFFICE' CHECK (channel IN ('OFFICE', 'COLLECTOR')),
    ADD COLUMN recorded_at    TIMESTAMP,
    ADD COLUMN failure_reason TEXT;

CREATE INDEX idx_transaction_pending_collector ON transaction (created_at)
    WHERE status = 'PENDING' AND channel = 'COLLECTOR';
CREATE INDEX idx_transaction_initiated_by ON transaction (initiated_by, created_at);

-- Collector withdrawal requests: created by collectors, approved or rejected and then paid at
-- the office by an administrator or agent. Creating a request never moves money; paying it
-- records a normal WITHDRAWAL transaction (linked here).
CREATE TABLE withdrawal_request (
    id                UUID PRIMARY KEY,
    idempotency_key   VARCHAR(100)   NOT NULL UNIQUE,
    member_account_id UUID           NOT NULL REFERENCES member_account (id),
    amount            DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    description       VARCHAR(500),
    status            VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    requested_by      UUID           NOT NULL REFERENCES staffs (id),
    created_at        TIMESTAMP      NOT NULL,
    recorded_at       TIMESTAMP,
    reviewed_by       UUID REFERENCES staffs (id),
    reviewed_at       TIMESTAMP,
    rejection_reason  TEXT,
    transaction_id    UUID REFERENCES transaction (id),
    CONSTRAINT chk_rejection_has_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT chk_paid_has_transaction CHECK (status <> 'PAID' OR transaction_id IS NOT NULL)
);

CREATE INDEX idx_withdrawal_request_status ON withdrawal_request (status, created_at);
CREATE INDEX idx_withdrawal_request_requested_by ON withdrawal_request (requested_by, created_at);

-- Every correction a collector makes within the 10 minutes, kept as its own record: what the
-- amount and account were before and after. The first correction's "previous" values are the
-- original record. Append-only, like the audit log.
CREATE TABLE collector_correction (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_type           VARCHAR(30)    NOT NULL CHECK (record_type IN ('PAYMENT', 'WITHDRAWAL_REQUEST')),
    record_id             UUID           NOT NULL,
    previous_amount       DECIMAL(19, 4) NOT NULL,
    previous_account_id   UUID           NOT NULL REFERENCES member_account (id),
    new_amount            DECIMAL(19, 4) NOT NULL,
    new_account_id        UUID           NOT NULL REFERENCES member_account (id),
    corrected_by          UUID           NOT NULL REFERENCES staffs (id),
    corrected_at          TIMESTAMP      NOT NULL
);

CREATE INDEX idx_collector_correction_record ON collector_correction (record_type, record_id, corrected_at);

CREATE TRIGGER trg_collector_correction_immutable
    BEFORE UPDATE OR DELETE ON collector_correction
    FOR EACH ROW EXECUTE FUNCTION reject_posted_change();

-- Collector cash is recorded against 1015 Cash with Collectors (standard practice, 2026-11-13).
INSERT INTO posting_rule (event, amount_part, entry_type, gl_account_id, match_side, match_account_type, description)
SELECT r.event, r.amount_part, r.entry_type, g.id, r.match_side, r.match_account_type, r.description
FROM (VALUES
    ('COLLECTOR_DEPOSIT',        'PRINCIPAL', 'DEBIT',  '1015', NULL,      NULL,      'Cash received by a collector'),
    ('COLLECTOR_DEPOSIT',        'PRINCIPAL', 'CREDIT', '2010', 'PRIMARY', 'SAVINGS', 'Owed to the member (savings)'),
    ('COLLECTOR_SHARE_PURCHASE', 'PRINCIPAL', 'DEBIT',  '1015', NULL,      NULL,      'Cash received by a collector'),
    ('COLLECTOR_SHARE_PURCHASE', 'PRINCIPAL', 'CREDIT', '3010', NULL,      NULL,      'Shares bought')
) AS r (event, amount_part, entry_type, account_code, match_side, match_account_type, description)
JOIN gl_account g ON g.account_code = r.account_code;
