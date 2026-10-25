ALTER TABLE member_account
    ADD COLUMN current_balance DECIMAL(19, 4) NOT NULL,
ADD COLUMN available_balance DECIMAL(19, 4) NOT NULL;