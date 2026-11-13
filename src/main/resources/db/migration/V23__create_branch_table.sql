CREATE TABLE branch_table (
                               id               UUID PRIMARY KEY,
                               name         VARCHAR(255) NOT NULL,
                               location     VARCHAR(255) NOT NULL,
                               created_at       TIMESTAMP    NOT NULL
);