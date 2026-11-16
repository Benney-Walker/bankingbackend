-- Transaction details for split entries (roadmap 5.x):
--   counterparty_account_id: the destination of a transfer, or the shares account that receives
--                            the share part of a first deposit
--   fee_amount:              withdrawal fee, taken on top of the amount
--   share_amount:            share part of a first deposit (the rest goes to member_account_id)
ALTER TABLE transaction
    ADD COLUMN counterparty_account_id UUID REFERENCES member_account (id),
    ADD COLUMN fee_amount   DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    ADD COLUMN share_amount DECIMAL(19, 4) NOT NULL DEFAULT 0 CHECK (share_amount >= 0),
    ADD CONSTRAINT chk_share_within_amount CHECK (share_amount <= amount);

CREATE INDEX idx_transaction_member_account ON transaction (member_account_id, created_at);
CREATE INDEX idx_transaction_counterparty_account ON transaction (counterparty_account_id, created_at);
CREATE INDEX idx_journal_entry_transaction ON journal_entry (transaction_id);
CREATE INDEX idx_journal_entry_gl_account ON journal_entry (gl_account_id);

-- No reversals (D16): journal lines can never be changed or removed, and recorded transactions
-- can't be deleted.
CREATE FUNCTION reject_posted_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed: posted records are final', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entry_immutable
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION reject_posted_change();

CREATE TRIGGER trg_journal_entry_no_truncate
    BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_posted_change();

CREATE TRIGGER trg_transaction_no_delete
    BEFORE DELETE ON transaction
    FOR EACH ROW EXECUTE FUNCTION reject_posted_change();
