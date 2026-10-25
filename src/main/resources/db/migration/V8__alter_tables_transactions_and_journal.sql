ALTER TABLE transaction
ADD COLUMN amount DECIMAL(19,4) NOT NULL CHECK ( amount > 0 ),
ADD COLUMN member_account_id UUID REFERENCES member_account(id),
ADD COLUMN initiated_by UUID REFERENCES staffs(id),
ADD COLUMN posted_at TIMESTAMP;

ALTER TABLE journal_entry
DROP COLUMN member_account_id;