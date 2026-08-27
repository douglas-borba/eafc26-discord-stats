-- Player X-Ray reads must distinguish unavailable advanced EA aggregates from
-- factual zeroes. Existing historical records intentionally remain unavailable.
ALTER TABLE player_match_stats
    ADD COLUMN advanced_coverage VARCHAR(16) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN advanced_dribbles_completed INT,
    ADD COLUMN advanced_beats INT,
    ADD COLUMN duration_seconds INT;

ALTER TABLE player_match_stats
    ADD CONSTRAINT player_match_stats_advanced_coverage_check
        CHECK (advanced_coverage IN ('UNAVAILABLE', 'PARTIAL', 'FULL')),
    ADD CONSTRAINT player_match_stats_advanced_dribbles_non_negative_check
        CHECK (advanced_dribbles_completed IS NULL OR advanced_dribbles_completed >= 0),
    ADD CONSTRAINT player_match_stats_advanced_beats_non_negative_check
        CHECK (advanced_beats IS NULL OR advanced_beats >= 0),
    ADD CONSTRAINT player_match_stats_duration_non_negative_check
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0);
