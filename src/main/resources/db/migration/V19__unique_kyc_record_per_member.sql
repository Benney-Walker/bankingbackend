-- One KYC record per member (confirmed business rule; roadmap 3.2). The entity mapping is
-- already @OneToOne, but V4 never enforced it in the database.
-- If existing data already breaks the rule, stop with a clear message instead of guessing which
-- record to keep; resolve those members by hand, then run the migration again.
DO $$
BEGIN
    IF EXISTS (SELECT member_id FROM member_kyc_records GROUP BY member_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V19: some members have more than one KYC record; resolve them before applying this migration';
    END IF;
END $$;

ALTER TABLE member_kyc_records
    ADD CONSTRAINT uq_member_kyc_records_member UNIQUE (member_id);
