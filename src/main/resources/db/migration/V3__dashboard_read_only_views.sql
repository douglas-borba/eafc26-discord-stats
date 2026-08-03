-- V3: Dashboard read-only views with RLS for public access via Supabase anon role.
--
-- Strategy:
--   security_invoker = true on views means queries execute as the calling user (anon).
--   Therefore anon needs SELECT on the underlying tables.
--   RLS is enabled on both tables with SELECT-only policies.
--   No INSERT/UPDATE/DELETE policies exist, so writes are denied by RLS.
--   This gives least-privilege read-only access through the anon role.

-- 1. Enable RLS on operational tables
ALTER TABLE public.canonical_matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_match_stats ENABLE ROW LEVEL SECURITY;

-- 2. SELECT-only policies for anon (read all rows — multi-club filtering is done at query level)
CREATE POLICY dashboard_select_matches ON public.canonical_matches
    FOR SELECT TO anon USING (true);

CREATE POLICY dashboard_select_player_stats ON public.player_match_stats
    FOR SELECT TO anon USING (true);

-- 3. Grant SELECT on underlying tables (required by security_invoker views)
GRANT SELECT ON public.canonical_matches TO anon;
GRANT SELECT ON public.player_match_stats TO anon;

-- 4. View: dashboard_matches (list queries — no payload)
CREATE OR REPLACE VIEW public.dashboard_matches
    WITH (security_invoker = true)
AS
SELECT
    match_id,
    club_id,
    opponent_club_id,
    played_at,
    match_type,
    our_club_name,
    opponent_club_name,
    our_score,
    opponent_score,
    outcome
FROM public.canonical_matches;

-- 5. View: dashboard_match_detail (single match detail — projected payload keys only)
--    Exposes only the 3 top-level JSONB keys used by the dashboard:
--    interpretation, footballMatch, stories.
--    Excludes operational metadata (canonical_schema_version, created_at, updated_at).
CREATE OR REPLACE VIEW public.dashboard_match_detail
    WITH (security_invoker = true)
AS
SELECT
    match_id,
    club_id,
    jsonb_build_object(
        'interpretation', payload->'interpretation',
        'footballMatch',  payload->'footballMatch',
        'stories',        payload->'stories'
    ) AS payload
FROM public.canonical_matches;

-- 6. View: dashboard_player_stats (player queries)
CREATE OR REPLACE VIEW public.dashboard_player_stats
    WITH (security_invoker = true)
AS
SELECT
    match_id,
    player_id,
    platform_name,
    pro_name,
    rating,
    goals,
    assists,
    shots,
    passes_completed,
    passes_attempted,
    tackles_completed,
    tackles_attempted,
    red_cards,
    man_of_the_match,
    played_at
FROM public.player_match_stats;

-- 7. Grant SELECT on views to anon
GRANT SELECT ON public.dashboard_matches TO anon;
GRANT SELECT ON public.dashboard_match_detail TO anon;
GRANT SELECT ON public.dashboard_player_stats TO anon;
