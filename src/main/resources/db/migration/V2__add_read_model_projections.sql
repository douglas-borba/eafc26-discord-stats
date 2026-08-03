-- Denormalized columns for efficient list queries without JSONB parsing
ALTER TABLE canonical_matches ADD COLUMN outcome VARCHAR(10);
ALTER TABLE canonical_matches ADD COLUMN our_score INT;
ALTER TABLE canonical_matches ADD COLUMN opponent_score INT;
ALTER TABLE canonical_matches ADD COLUMN our_club_name VARCHAR(255);
ALTER TABLE canonical_matches ADD COLUMN opponent_club_name VARCHAR(255);

-- Backfill from JSONB payload
UPDATE canonical_matches SET
    outcome = payload->'interpretation'->'result'->>'outcome',
    our_score = (payload->'interpretation'->'result'->>'ourScore')::int,
    opponent_score = (payload->'interpretation'->'result'->>'opponentScore')::int,
    our_club_name = (
        SELECT p->'club'->>'name'
        FROM jsonb_array_elements(payload->'footballMatch'->'participants') AS p
        WHERE p->'club'->>'id' = club_id
        LIMIT 1
    ),
    opponent_club_name = (
        SELECT p->'club'->>'name'
        FROM jsonb_array_elements(payload->'footballMatch'->'participants') AS p
        WHERE p->'club'->>'id' = opponent_club_id
        LIMIT 1
    );

CREATE INDEX idx_canonical_matches_outcome ON canonical_matches (outcome);

-- Player match statistics for efficient player queries
CREATE TABLE player_match_stats (
    match_id        VARCHAR(255) NOT NULL REFERENCES canonical_matches(match_id) ON DELETE CASCADE,
    player_id       VARCHAR(255) NOT NULL,
    platform_name   VARCHAR(255),
    pro_name        VARCHAR(255),
    rating          DECIMAL(4,2),
    goals           INT NOT NULL DEFAULT 0,
    assists         INT NOT NULL DEFAULT 0,
    shots           INT NOT NULL DEFAULT 0,
    passes_completed INT NOT NULL DEFAULT 0,
    passes_attempted INT NOT NULL DEFAULT 0,
    tackles_completed INT NOT NULL DEFAULT 0,
    tackles_attempted INT NOT NULL DEFAULT 0,
    red_cards       INT NOT NULL DEFAULT 0,
    man_of_the_match BOOLEAN NOT NULL DEFAULT FALSE,
    played_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (match_id, player_id)
);

CREATE INDEX idx_player_match_stats_player_id ON player_match_stats (player_id);
CREATE INDEX idx_player_match_stats_played_at ON player_match_stats (played_at DESC);
CREATE INDEX idx_player_match_stats_platform_name ON player_match_stats (platform_name);

-- Backfill player stats from JSONB payload (perspective club players only)
INSERT INTO player_match_stats (
    match_id, player_id, platform_name, pro_name, rating,
    goals, assists, shots, passes_completed, passes_attempted,
    tackles_completed, tackles_attempted, red_cards, man_of_the_match, played_at
)
SELECT
    cm.match_id,
    player->'player'->>'id',
    player->'player'->>'platformName',
    player->'player'->>'proName',
    (player->>'rating')::decimal(4,2),
    COALESCE((player->'attacking'->>'goals')::int, 0),
    COALESCE((player->'attacking'->>'assists')::int, 0),
    COALESCE((player->'attacking'->>'shots')::int, 0),
    COALESCE((player->'passing'->>'completed')::int, 0),
    COALESCE((player->'passing'->>'attempted')::int, 0),
    COALESCE((player->'defending'->>'tacklesCompleted')::int, 0),
    COALESCE((player->'defending'->>'tacklesAttempted')::int, 0),
    COALESCE((player->'discipline'->>'redCards')::int, 0),
    COALESCE((player->'eaRecognition'->>'manOfTheMatch')::boolean, false),
    cm.played_at
FROM canonical_matches cm,
     jsonb_array_elements(cm.payload->'footballMatch'->'participants') AS participant,
     jsonb_array_elements(participant->'players') AS player
WHERE participant->'club'->>'id' = cm.club_id;
