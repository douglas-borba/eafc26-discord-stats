-- FC26 historical rows are the only rows that predate game-version provenance.
-- The defaults classify them explicitly without rewriting their canonical payloads.

ALTER TABLE monitored_clubs
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT monitored_clubs_game_version_check CHECK (game_version IN ('FC26', 'FC27'));

ALTER TABLE canonical_matches
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT canonical_matches_game_version_check CHECK (game_version IN ('FC26', 'FC27'));

ALTER TABLE player_match_stats
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT player_match_stats_game_version_check CHECK (game_version IN ('FC26', 'FC27'));

-- Canonical and projection identity includes the producer contract. Existing
-- FC26 values retain their keys; FC27 may never overwrite them accidentally.
ALTER TABLE player_match_stats DROP CONSTRAINT IF EXISTS player_match_stats_canonical_match_fkey;
ALTER TABLE player_match_stats DROP CONSTRAINT IF EXISTS player_match_stats_pkey;
ALTER TABLE canonical_matches DROP CONSTRAINT IF EXISTS canonical_matches_pkey;
ALTER TABLE canonical_matches ADD PRIMARY KEY (game_version, club_id, match_id);
ALTER TABLE player_match_stats ADD PRIMARY KEY (game_version, club_id, match_id, player_id);
ALTER TABLE player_match_stats
    ADD CONSTRAINT player_match_stats_canonical_match_fkey
    FOREIGN KEY (game_version, club_id, match_id)
    REFERENCES canonical_matches (game_version, club_id, match_id)
    ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_canonical_matches_game_club_played
    ON canonical_matches (game_version, club_id, played_at DESC);
CREATE INDEX IF NOT EXISTS idx_player_match_stats_game_club_player_played
    ON player_match_stats (game_version, club_id, player_id, played_at DESC);

ALTER TABLE discord_publication_state
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT discord_publication_state_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE discord_publication_state DROP CONSTRAINT IF EXISTS discord_publication_state_pkey;
ALTER TABLE discord_publication_state ADD PRIMARY KEY (game_version, club_id, match_id);

ALTER TABLE synchronization_gaps
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT synchronization_gaps_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE synchronization_gaps DROP CONSTRAINT IF EXISTS synchronization_gaps_pkey;
ALTER TABLE synchronization_gaps ADD PRIMARY KEY (game_version, club_id);

ALTER TABLE explorer_observations
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT explorer_observations_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE explorer_observations DROP CONSTRAINT IF EXISTS uq_explorer_observations_identity;
ALTER TABLE explorer_observations
    ADD CONSTRAINT uq_explorer_observations_versioned_identity
    UNIQUE (game_version, club_id, match_id, player_id, phrase);
CREATE INDEX IF NOT EXISTS idx_explorer_observations_game_player_phrase
    ON explorer_observations (game_version, club_id, player_id, phrase, updated_at DESC);

ALTER TABLE explorer_controlled_observations
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT explorer_controlled_observations_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE explorer_controlled_observations DROP CONSTRAINT IF EXISTS uq_explorer_controlled_observation_identity;
ALTER TABLE explorer_controlled_observations
    ADD CONSTRAINT uq_explorer_controlled_observation_versioned_identity
    UNIQUE (game_version, club_id, match_id, player_id, phrase, aggregate_index, code);
CREATE INDEX IF NOT EXISTS idx_explorer_controlled_observations_game_candidate
    ON explorer_controlled_observations (game_version, club_id, player_id, phrase, aggregate_index, code, created_at DESC);

ALTER TABLE match_editorial_presentations
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT match_editorial_presentations_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE match_editorial_presentations DROP CONSTRAINT IF EXISTS match_editorial_presentations_pkey;
ALTER TABLE match_editorial_presentations ADD PRIMARY KEY (game_version, club_id, match_id);

ALTER TABLE editorial_panoramas
    ADD COLUMN IF NOT EXISTS game_version VARCHAR(16) NOT NULL DEFAULT 'FC26',
    ADD CONSTRAINT editorial_panoramas_game_version_check CHECK (game_version IN ('FC26', 'FC27'));
ALTER TABLE editorial_panoramas DROP CONSTRAINT IF EXISTS uq_panorama_context;
ALTER TABLE editorial_panoramas ADD CONSTRAINT uq_panorama_versioned_context UNIQUE (game_version, club_id, context_key);

DROP VIEW IF EXISTS public.dashboard_player_stats;
DROP VIEW IF EXISTS public.dashboard_match_detail;
DROP VIEW IF EXISTS public.dashboard_matches;
CREATE VIEW public.dashboard_matches WITH (security_invoker = true) AS
SELECT game_version, match_id, club_id, opponent_club_id, played_at, match_type,
       our_club_name, opponent_club_name, our_score, opponent_score, outcome
FROM public.canonical_matches;
CREATE VIEW public.dashboard_match_detail WITH (security_invoker = true) AS
SELECT game_version, match_id, club_id,
       jsonb_build_object(
           'interpretation', payload->'interpretation',
           'footballMatch', payload->'footballMatch',
           'stories', payload->'stories',
           'gameVersion', payload->'gameVersion'
       ) AS payload
FROM public.canonical_matches;
CREATE VIEW public.dashboard_player_stats WITH (security_invoker = true) AS
SELECT game_version, club_id, match_id, player_id, platform_name, pro_name, rating,
       goals, assists, shots, passes_completed, passes_attempted,
       tackles_completed, tackles_attempted, red_cards, man_of_the_match, played_at
FROM public.player_match_stats;
GRANT SELECT ON public.dashboard_matches TO anon;
GRANT SELECT ON public.dashboard_match_detail TO anon;
GRANT SELECT ON public.dashboard_player_stats TO anon;

DROP VIEW IF EXISTS public.dashboard_editorial_presentations;
CREATE VIEW public.dashboard_editorial_presentations WITH (security_invoker = true) AS
SELECT game_version, club_id, match_id, played_at, presentation
FROM match_editorial_presentations;
GRANT SELECT ON public.dashboard_editorial_presentations TO anon;

DROP VIEW IF EXISTS public.dashboard_panoramas;
CREATE VIEW public.dashboard_panoramas WITH (security_invoker = true) AS
SELECT game_version, club_id, narrative, status, generated_at
FROM editorial_panoramas
WHERE status = 'success' AND narrative IS NOT NULL;
GRANT SELECT ON public.dashboard_panoramas TO anon;
