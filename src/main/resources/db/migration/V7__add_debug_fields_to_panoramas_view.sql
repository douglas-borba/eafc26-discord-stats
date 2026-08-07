-- V7: Add debug fields to dashboard_panoramas view for investigation
--
-- Temporarily adds context_key and match_ids to the dashboard view
-- to allow frontend investigation of panorama selection logic.

DROP VIEW IF EXISTS public.dashboard_panoramas;

CREATE OR REPLACE VIEW public.dashboard_panoramas
    WITH (security_invoker = true)
AS
SELECT
    club_id,
    context_key,
    match_ids,
    narrative,
    status,
    generated_at
FROM editorial_panoramas
WHERE status = 'success' AND narrative IS NOT NULL;

COMMENT ON VIEW public.dashboard_panoramas IS
    'Read-only view exposing successful AI panoramas for the Next.js dashboard. Includes context_key and match_ids for debugging.';

GRANT SELECT ON public.dashboard_panoramas TO anon;

