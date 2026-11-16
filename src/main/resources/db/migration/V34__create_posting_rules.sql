-- Posting rules (owner decision 2026-11-13: rules are data, not code). Each row says: for this
-- event, put this part of the amount on this side of this GL account. A rule can be limited to
-- a member account type on one side of the transaction:
--   match_side PRIMARY      = the transaction's member account (the source, for a transfer)
--   match_side COUNTERPARTY = the other account (the destination of a transfer, or the shares
--                             account that receives the share part of a first deposit)
-- Amount parts: PRINCIPAL (the transaction amount), FEE, SHARE (share part of a first deposit),
-- REMAINDER (first deposit minus the share part). Lines with a zero amount are skipped, and the
-- backend refuses to post a journal whose debits and credits don't balance.
--
-- Seeded from standard double-entry practice (drafted 2026-11-13, to be updated if needed). All
-- cash here is office cash (1010); collector cash (1015) arrives with the collector flow.
CREATE TABLE posting_rule (
    id                 SERIAL PRIMARY KEY,
    event              VARCHAR(40)  NOT NULL,
    amount_part        VARCHAR(20)  NOT NULL CHECK (amount_part IN ('PRINCIPAL', 'FEE', 'SHARE', 'REMAINDER')),
    entry_type         VARCHAR(10)  NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    gl_account_id      INT          NOT NULL REFERENCES gl_account (id),
    match_side         VARCHAR(20)  CHECK (match_side IN ('PRIMARY', 'COUNTERPARTY')),
    match_account_type VARCHAR(20)  CHECK (match_account_type IN ('SAVINGS', 'CURRENT', 'SHARES')),
    description        VARCHAR(255),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_posting_rule_match CHECK ((match_side IS NULL) = (match_account_type IS NULL))
);

CREATE INDEX idx_posting_rule_event ON posting_rule (event);

INSERT INTO posting_rule (event, amount_part, entry_type, gl_account_id, match_side, match_account_type, description)
SELECT r.event, r.amount_part, r.entry_type, g.id, r.match_side, r.match_account_type, r.description
FROM (VALUES
    -- Deposit at the office
    ('DEPOSIT', 'PRINCIPAL', 'DEBIT',  '1010', NULL,      NULL,      'Cash received at the office'),
    ('DEPOSIT', 'PRINCIPAL', 'CREDIT', '2010', 'PRIMARY', 'SAVINGS', 'Owed to the member (savings)'),
    ('DEPOSIT', 'PRINCIPAL', 'CREDIT', '2015', 'PRIMARY', 'CURRENT', 'Owed to the member (current)'),
    -- First deposit: the share part buys the mandatory shares, the rest is the member's deposit
    ('FIRST_DEPOSIT', 'PRINCIPAL', 'DEBIT',  '1010', NULL,      NULL,      'Cash received at the office'),
    ('FIRST_DEPOSIT', 'SHARE',     'CREDIT', '3010', NULL,      NULL,      'Mandatory share purchase'),
    ('FIRST_DEPOSIT', 'REMAINDER', 'CREDIT', '2010', 'PRIMARY', 'SAVINGS', 'Rest owed to the member (savings)'),
    ('FIRST_DEPOSIT', 'REMAINDER', 'CREDIT', '2015', 'PRIMARY', 'CURRENT', 'Rest owed to the member (current)'),
    -- Additional share purchase in cash
    ('SHARE_PURCHASE', 'PRINCIPAL', 'DEBIT',  '1010', NULL, NULL, 'Cash received at the office'),
    ('SHARE_PURCHASE', 'PRINCIPAL', 'CREDIT', '3010', NULL, NULL, 'Shares bought'),
    -- Withdrawal paid at the office; the fee is taken on top of the amount
    ('WITHDRAWAL', 'PRINCIPAL', 'DEBIT',  '2010', 'PRIMARY', 'SAVINGS', 'Owed to the member less (savings)'),
    ('WITHDRAWAL', 'PRINCIPAL', 'DEBIT',  '2015', 'PRIMARY', 'CURRENT', 'Owed to the member less (current)'),
    ('WITHDRAWAL', 'PRINCIPAL', 'CREDIT', '1010', NULL,      NULL,      'Cash paid out at the office'),
    ('WITHDRAWAL', 'FEE',       'DEBIT',  '2010', 'PRIMARY', 'SAVINGS', 'Fee taken from the member (savings)'),
    ('WITHDRAWAL', 'FEE',       'DEBIT',  '2015', 'PRIMARY', 'CURRENT', 'Fee taken from the member (current)'),
    ('WITHDRAWAL', 'FEE',       'CREDIT', '4020', NULL,      NULL,      'Withdrawal fee earned'),
    -- Transfer between savings/current accounts, of the same member or of two members
    ('TRANSFER', 'PRINCIPAL', 'DEBIT',  '2010', 'PRIMARY',      'SAVINGS', 'Owed to the sender less (savings)'),
    ('TRANSFER', 'PRINCIPAL', 'DEBIT',  '2015', 'PRIMARY',      'CURRENT', 'Owed to the sender less (current)'),
    ('TRANSFER', 'PRINCIPAL', 'CREDIT', '2010', 'COUNTERPARTY', 'SAVINGS', 'Owed to the receiver (savings)'),
    ('TRANSFER', 'PRINCIPAL', 'CREDIT', '2015', 'COUNTERPARTY', 'CURRENT', 'Owed to the receiver (current)')
) AS r (event, amount_part, entry_type, account_code, match_side, match_account_type, description)
JOIN gl_account g ON g.account_code = r.account_code;
