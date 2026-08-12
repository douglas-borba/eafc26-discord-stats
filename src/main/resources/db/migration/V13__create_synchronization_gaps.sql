-- Durable audit state for bounded EA synchronization gaps.
-- This is state, not an operational event, and must not be removed by event retention.
CREATE TABLE synchronization_gaps (
    club_id                    TEXT PRIMARY KEY,
    anchor_match_id            TEXT NOT NULL,
    first_observable_match_id  TEXT,
    state                      TEXT NOT NULL CHECK (state IN ('OPEN', 'RESOLVED')),
    opened_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_observed_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
