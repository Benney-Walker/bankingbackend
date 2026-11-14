-- Editable financial settings (owner decision 2026-11-14). The credit union hasn't set these
-- yet, so the seeded values below are MOCK VALUES for development and demos. Administrators
-- will change them from the desktop app through the backend; they are data, not code.
--
-- Exactly one row (id = 1). The CHECK constraints repeat the backend's validation so a bad
-- value can't be stored even by a direct SQL update.
CREATE TABLE financial_settings (
    id                     INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),

    -- Mandatory share purchase taken from a member's first deposit (fixed amount, GHS).
    initial_share_amount   DECIMAL(19, 4) NOT NULL CHECK (initial_share_amount >= 0),

    -- Withdrawals.
    withdrawal_min_balance DECIMAL(19, 4) NOT NULL CHECK (withdrawal_min_balance >= 0),
    withdrawal_daily_limit DECIMAL(19, 4) NOT NULL CHECK (withdrawal_daily_limit > 0),
    withdrawal_fee_enabled BOOLEAN        NOT NULL,
    withdrawal_fee_type    VARCHAR(20)    NOT NULL CHECK (withdrawal_fee_type IN ('FLAT', 'PERCENTAGE')),
    -- GHS for FLAT; percent of the amount withdrawn for PERCENTAGE (1.5 means 1.5%).
    withdrawal_fee_value   DECIMAL(19, 4) NOT NULL CHECK (withdrawal_fee_value >= 0),

    -- Optimistic-locking version: an edit based on an older copy is rejected.
    version                BIGINT         NOT NULL DEFAULT 0,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- Staff code of the last editor; NULL for the seeded values.
    updated_by             VARCHAR(50),

    CONSTRAINT chk_withdrawal_fee_percentage
        CHECK (withdrawal_fee_type <> 'PERCENTAGE' OR withdrawal_fee_value <= 100)
);

-- MOCK VALUES: replace with the credit union's real settings.
INSERT INTO financial_settings (id, initial_share_amount, withdrawal_min_balance, withdrawal_daily_limit,
                                withdrawal_fee_enabled, withdrawal_fee_type, withdrawal_fee_value)
VALUES (1, 50.0000, 20.0000, 10000.0000, TRUE, 'FLAT', 2.0000);
