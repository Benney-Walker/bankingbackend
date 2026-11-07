-- Transactional outbox (roadmap 2.1, decision D6). A message is written here in the same
-- database transaction as the change it announces, so it can't be lost if RabbitMQ is down.
-- OutboxPublisher sends it right after commit, and a scheduled job retries anything unsent.
-- payload holds the message body exactly as it will be sent (JSON from the AMQP converter).
CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    exchange         VARCHAR(255) NOT NULL,
    routing_key      VARCHAR(255) NOT NULL,
    payload          TEXT         NOT NULL,
    content_type     VARCHAR(100),
    content_encoding VARCHAR(50),
    type_id          VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL,
    published_at     TIMESTAMP,
    attempts         INT          NOT NULL DEFAULT 0,
    last_error       TEXT
);

-- The retry job only ever looks for unsent rows, oldest first.
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
