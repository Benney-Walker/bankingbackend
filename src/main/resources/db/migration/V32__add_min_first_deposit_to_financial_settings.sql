-- Minimum first deposit (owner decision 2026-11-14), validated by the backend. It can't be less
-- than the share amount, because the share purchase is taken out of the first deposit.
-- 100.0000 is a MOCK VALUE, like the rest of the V31 settings.
ALTER TABLE financial_settings
    ADD COLUMN min_first_deposit DECIMAL(19, 4) NOT NULL DEFAULT 100.0000 CHECK (min_first_deposit >= 0);

ALTER TABLE financial_settings ALTER COLUMN min_first_deposit DROP DEFAULT;

ALTER TABLE financial_settings
    ADD CONSTRAINT chk_min_first_deposit_covers_shares CHECK (min_first_deposit >= initial_share_amount);
