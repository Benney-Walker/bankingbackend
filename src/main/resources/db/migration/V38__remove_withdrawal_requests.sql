-- Withdrawal requests removed (owner decision 2026-11-15): collectors no longer create them, and
-- a member who wants to withdraw goes to the nearest branch, where staff record a normal
-- withdrawal. A new migration rather than an edit to V37, so databases that already ran V37
-- still start.

-- Corrections of withdrawal requests go with them. The table is append-only, so the guard
-- trigger is switched off only for this delete.
ALTER TABLE collector_correction DISABLE TRIGGER trg_collector_correction_immutable;
DELETE FROM collector_correction WHERE record_type = 'WITHDRAWAL_REQUEST';
ALTER TABLE collector_correction ENABLE TRIGGER trg_collector_correction_immutable;

ALTER TABLE collector_correction DROP CONSTRAINT collector_correction_record_type_check;
ALTER TABLE collector_correction
    ADD CONSTRAINT collector_correction_record_type_check CHECK (record_type IN ('PAYMENT'));

DROP TABLE withdrawal_request;
