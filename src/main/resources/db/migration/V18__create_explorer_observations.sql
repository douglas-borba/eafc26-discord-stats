CREATE TABLE explorer_observations (
    id BIGSERIAL PRIMARY KEY,
    club_id TEXT NOT NULL,
    match_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    phrase TEXT NOT NULL,
    observed_count INTEGER NOT NULL CHECK (observed_count >= 0),
    completeness VARCHAR(20) NOT NULL DEFAULT 'AT_LEAST'
        CHECK (completeness IN ('AT_LEAST', 'EXACT')),
    note TEXT,
    observed_position_context TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_explorer_observations_identity
        UNIQUE (club_id, match_id, player_id, phrase)
);

CREATE INDEX idx_explorer_observations_player_phrase
    ON explorer_observations (club_id, player_id, phrase, updated_at DESC);

CREATE INDEX idx_explorer_observations_match_player
    ON explorer_observations (club_id, match_id, player_id);
