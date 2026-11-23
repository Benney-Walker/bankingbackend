-- Collector location tracking (owner decisions 2026-11-23, increment #6, D34). The app sends a
-- point every 5 minutes and on each payment, including points taken offline. Points are kept
-- for 90 days. recorded_at is the phone's time; received_at the server's.
CREATE TABLE collector_location (
    id                       BIGSERIAL PRIMARY KEY,
    collector_id             UUID          NOT NULL REFERENCES staffs (id),
    latitude                 NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude                NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    accuracy_m               NUMERIC(8, 2) NOT NULL CHECK (accuracy_m >= 0),
    battery_percent          SMALLINT CHECK (battery_percent BETWEEN 0 AND 100),
    trigger                  VARCHAR(20)   NOT NULL CHECK (trigger IN ('EVERY_5_MIN', 'PAYMENT')),
    payment_idempotency_key  VARCHAR(100),
    recorded_at              TIMESTAMP     NOT NULL,
    received_at              TIMESTAMP     NOT NULL,
    CONSTRAINT chk_collector_location_payment_key CHECK (
        (trigger = 'PAYMENT') = (payment_idempotency_key IS NOT NULL)),
    -- A re-sent point (offline sync retried) is the same point: stored once.
    CONSTRAINT uq_collector_location_point UNIQUE (collector_id, recorded_at, trigger)
);

CREATE INDEX idx_collector_location_trail ON collector_location (collector_id, recorded_at);
CREATE INDEX idx_collector_location_received ON collector_location (collector_id, received_at);
CREATE INDEX idx_collector_location_recorded ON collector_location (recorded_at);
