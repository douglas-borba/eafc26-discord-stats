CREATE TABLE monitored_clubs (
    club_id                     VARCHAR(255)             NOT NULL PRIMARY KEY,
    display_name                VARCHAR(255)             NOT NULL,
    platform                    VARCHAR(50)              NOT NULL,
    monitoring_enabled          BOOLEAN                  NOT NULL DEFAULT TRUE,
    discord_webhook_secret_ref  VARCHAR(255),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT monitored_clubs_webhook_ref_not_url CHECK (
        discord_webhook_secret_ref IS NULL OR
        discord_webhook_secret_ref !~* '^https?://'
    )
);
