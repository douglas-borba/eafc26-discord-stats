-- discord_publication_state: persistent publication state per (club_id, match_id)
CREATE TABLE discord_publication_state (
    club_id         TEXT NOT NULL,
    match_id        TEXT NOT NULL,
    state           TEXT NOT NULL,  -- DELIVERING, DELIVERED, DELIVERY_UNCERTAIN, FAILED_PERMANENT, BASELINED
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_error      TEXT,
    last_http_status INTEGER,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (club_id, match_id)
);

CREATE INDEX idx_publication_state_club ON discord_publication_state(club_id);
CREATE INDEX idx_publication_state_club_state ON discord_publication_state(club_id, state);

-- operational_events: high-value operational events for admin diagnostics
CREATE TABLE operational_events (
    id          BIGSERIAL PRIMARY KEY,
    club_id     TEXT,
    match_id    TEXT,
    event_type  TEXT NOT NULL,
    phase       TEXT,
    status      TEXT NOT NULL,  -- SUCCESS, FAILURE, WARNING, INFO
    message     TEXT,
    error_code  TEXT,
    duration_ms BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_op_events_club ON operational_events(club_id, created_at DESC);
CREATE INDEX idx_op_events_type ON operational_events(event_type, created_at DESC);
CREATE INDEX idx_op_events_created ON operational_events(created_at DESC);
