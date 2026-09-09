CREATE TABLE explorer_controlled_observations (
    id BIGSERIAL PRIMARY KEY,
    club_id TEXT NOT NULL,
    match_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    phrase TEXT NOT NULL,
    observed_count INTEGER NOT NULL CHECK (observed_count >= 0),
    completeness VARCHAR(20) NOT NULL CHECK (completeness IN ('AT_LEAST', 'EXACT')),
    aggregate_index INTEGER NOT NULL CHECK (aggregate_index BETWEEN 0 AND 3),
    code INTEGER NOT NULL CHECK (code >= 0),
    experiment_type VARCHAR(20) NOT NULL CHECK (experiment_type IN ('COUNT_MATCH', 'DISCRIMINATION')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_explorer_controlled_observation_identity
      UNIQUE (club_id, match_id, player_id, phrase, aggregate_index, code)
);

CREATE INDEX idx_explorer_controlled_observations_candidate
  ON explorer_controlled_observations (club_id, player_id, phrase, aggregate_index, code, created_at DESC);
