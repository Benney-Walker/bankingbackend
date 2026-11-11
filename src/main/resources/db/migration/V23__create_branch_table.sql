CREATE TABLE outbox_events (
                               id               UUID PRIMARY KEY,
                               name         VARCHAR(255) NOT NULL,
                               location     VARCHAR(255) NOT NULL,
                               created_at       TIMESTAMP    NOT NULL,
);