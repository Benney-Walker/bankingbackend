-- Collector records arrive final (owner decision 2026-11-23). The collector app keeps a new record
-- for 5 minutes, during which the collector can correct it in the app, then sends it. The backend
-- applies it on arrival and no longer counts a correction window.
--
-- The app now sends when the record was created and last edited:
--   created_at  - for collector records, the phone's creation time (the moment the cash was taken);
--                 it decides the collector's day and the statement date
--   updated_at  - the phone's last-edit time; NULL for office transactions, which are never edited
--   received_at - when the backend received the record (for office transactions, the same as
--                 created_at)
ALTER TABLE transaction
    ADD COLUMN received_at TIMESTAMP,
    ADD COLUMN updated_at  TIMESTAMP;

UPDATE transaction SET received_at = COALESCE(created_at, posted_at, now());

-- Offline records already carried the phone's time in recorded_at: it becomes created_at, so every
-- collector record follows the new meaning.
UPDATE transaction SET created_at = recorded_at WHERE recorded_at IS NOT NULL;

ALTER TABLE transaction ALTER COLUMN received_at SET NOT NULL;
ALTER TABLE transaction DROP COLUMN recorded_at;

-- collector_correction is kept as history of corrections made before this change; nothing writes
-- to it any more.
