-- Collector shortage repayment (owner decisions 2026-11-23, increment #8). A shortage recorded on
-- an approved daily submission stays in 1015 Cash with Collectors. The collector pays it back at
-- their branch (the agent records it) or at the main office (an administrator records it). Each
-- repayment moves the cash from 1015 to 1010 Cash on Hand. Unpaid shortages are only tracked.
-- What a collector owes = their submissions' shortages minus their repayments.
CREATE TABLE collector_shortage_repayment (
    id              UUID PRIMARY KEY,
    collector_id    UUID           NOT NULL REFERENCES staffs (id),
    amount          DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    transaction_id  UUID           NOT NULL UNIQUE REFERENCES transaction (id),
    received_by     UUID           NOT NULL REFERENCES staffs (id),
    received_at     TIMESTAMP      NOT NULL,
    note            VARCHAR(255)
);

CREATE INDEX idx_collector_shortage_repayment_collector ON collector_shortage_repayment (collector_id, received_at);

-- Repayments are final, like the transactions behind them.
CREATE TRIGGER trg_collector_shortage_repayment_immutable
    BEFORE UPDATE OR DELETE ON collector_shortage_repayment
    FOR EACH ROW EXECUTE FUNCTION reject_posted_change();

CREATE INDEX idx_collector_submission_shortage ON collector_submission (collector_id) WHERE shortage_amount > 0;

INSERT INTO posting_rule (event, amount_part, entry_type, gl_account_id, match_side, match_account_type, description)
SELECT r.event, r.amount_part, r.entry_type, g.id, NULL, NULL, r.description
FROM (VALUES
    ('COLLECTOR_SHORTAGE_REPAYMENT', 'PRINCIPAL', 'DEBIT',  '1010', 'Shortage cash repaid at the office'),
    ('COLLECTOR_SHORTAGE_REPAYMENT', 'PRINCIPAL', 'CREDIT', '1015', 'Clears the shortage left with the collector')
) AS r (event, amount_part, entry_type, account_code, description)
JOIN gl_account g ON g.account_code = r.account_code;
