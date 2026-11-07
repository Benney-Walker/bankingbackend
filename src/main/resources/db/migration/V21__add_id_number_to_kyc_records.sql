-- ID document number typed on the KYC upload form (owner decisions 2026-11-07, roadmap 3.5).
-- It's compared with what OCR reads from the document, and no two members may share one.
-- Nullable only so existing rows migrate; new submissions always provide it. PostgreSQL's
-- unique constraint allows multiple NULLs.
ALTER TABLE member_kyc_records
    ADD COLUMN id_number VARCHAR(50);

ALTER TABLE member_kyc_records
    ADD CONSTRAINT uq_member_kyc_records_id_number UNIQUE (id_number);
