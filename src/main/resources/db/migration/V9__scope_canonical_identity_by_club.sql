DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM canonical_matches WHERE club_id IS NULL OR btrim(club_id) = '') THEN
        RAISE EXCEPTION 'canonical_matches contains rows without club_id';
    END IF;
END $$;

DROP VIEW IF EXISTS public.dashboard_player_stats;
DROP VIEW IF EXISTS public.dashboard_match_detail;
DROP VIEW IF EXISTS public.dashboard_matches;

ALTER TABLE player_match_stats ADD COLUMN club_id VARCHAR(255);
UPDATE player_match_stats ps
SET club_id = cm.club_id
FROM canonical_matches cm
WHERE ps.match_id = cm.match_id;
ALTER TABLE player_match_stats ALTER COLUMN club_id SET NOT NULL;

ALTER TABLE player_match_stats DROP CONSTRAINT player_match_stats_match_id_fkey;
ALTER TABLE player_match_stats DROP CONSTRAINT player_match_stats_pkey;
ALTER TABLE canonical_matches DROP CONSTRAINT canonical_matches_pkey;

ALTER TABLE canonical_matches ADD PRIMARY KEY (club_id, match_id);
ALTER TABLE player_match_stats ADD PRIMARY KEY (club_id, match_id, player_id);
ALTER TABLE player_match_stats ADD CONSTRAINT player_match_stats_canonical_match_fkey
    FOREIGN KEY (club_id, match_id)
    REFERENCES canonical_matches (club_id, match_id)
    ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_player_match_stats_player_id;
DROP INDEX IF EXISTS idx_player_match_stats_played_at;
CREATE INDEX idx_player_match_stats_club_player_played
    ON player_match_stats (club_id, player_id, played_at DESC);
CREATE INDEX idx_player_match_stats_club_played
    ON player_match_stats (club_id, played_at DESC);

CREATE VIEW public.dashboard_matches WITH (security_invoker = true) AS
SELECT match_id, club_id, opponent_club_id, played_at, match_type,
       our_club_name, opponent_club_name, our_score, opponent_score, outcome
FROM public.canonical_matches;

CREATE VIEW public.dashboard_match_detail WITH (security_invoker = true) AS
SELECT match_id, club_id,
       jsonb_build_object(
           'interpretation', payload->'interpretation',
           'footballMatch', payload->'footballMatch',
           'stories', payload->'stories'
       ) AS payload
FROM public.canonical_matches;

CREATE VIEW public.dashboard_player_stats WITH (security_invoker = true) AS
SELECT club_id, match_id, player_id, platform_name, pro_name, rating,
       goals, assists, shots, passes_completed, passes_attempted,
       tackles_completed, tackles_attempted, red_cards, man_of_the_match, played_at
FROM public.player_match_stats;

GRANT SELECT ON public.dashboard_matches TO anon;
GRANT SELECT ON public.dashboard_match_detail TO anon;
GRANT SELECT ON public.dashboard_player_stats TO anon;
