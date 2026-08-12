CREATE TABLE discord_webhook_secrets (
    reference               TEXT                        NOT NULL PRIMARY KEY,
    club_id                 VARCHAR(255)                NOT NULL,
    encrypted_webhook_url   BYTEA                       NOT NULL,
    nonce                   BYTEA                       NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT discord_webhook_secrets_reference_not_url CHECK (reference !~* '^https?://'),
    CONSTRAINT discord_webhook_secrets_nonce_length CHECK (octet_length(nonce) = 12)
);

CREATE INDEX idx_discord_webhook_secrets_club_id
    ON discord_webhook_secrets (club_id);
