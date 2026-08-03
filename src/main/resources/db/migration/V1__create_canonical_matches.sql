CREATE TABLE IF NOT EXISTS canonical_matches (
    match_id            VARCHAR(255)                NOT NULL PRIMARY KEY,
    club_id             VARCHAR(255)                NOT NULL,
    opponent_club_id    VARCHAR(255),
    played_at           TIMESTAMP WITH TIME ZONE    NOT NULL,
    match_type          VARCHAR(50),
    canonical_schema_version INTEGER                NOT NULL,
    payload             JSONB                       NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now()
);

CREATE INDEX idx_canonical_matches_club_id ON canonical_matches (club_id);
CREATE INDEX idx_canonical_matches_opponent_club_id ON canonical_matches (opponent_club_id);
CREATE INDEX idx_canonical_matches_played_at ON canonical_matches (played_at);
CREATE INDEX idx_canonical_matches_club_played_at ON canonical_matches (club_id, played_at DESC);
