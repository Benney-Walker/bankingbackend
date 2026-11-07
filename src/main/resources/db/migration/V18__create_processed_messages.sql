-- Consumer-side de-duplication (roadmap 2.2). RabbitMQ delivery is at-least-once, so the same
-- message can arrive twice. A consumer records the message ID in the same transaction as the
-- work it does; if the ID is already recorded for that consumer, the message is skipped.
CREATE TABLE processed_messages (
    message_id   VARCHAR(255) NOT NULL,
    consumer     VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_processed_messages PRIMARY KEY (message_id, consumer)
);
