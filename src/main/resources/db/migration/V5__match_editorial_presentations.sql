-- V5__match_editorial_presentations.sql
--
-- Creates a separate table for editorial presentations (read model).
--
-- This table is NOT part of the canonical domain. It stores presentation-ready
-- data derived from canonical matches, including editorial phrases and formatted
-- copy.
--
-- The same canonical match may have different editorial presentations if the
-- phrase bank changes. This separation allows copy evolution without requiring
-- re-interpretation of football decisions.

CREATE TABLE IF NOT EXISTS match_editorial_presentations (
    club_id             TEXT NOT NULL,
    match_id            TEXT NOT NULL,
    played_at           TIMESTAMPTZ NOT NULL,
    schema_version      INTEGER NOT NULL DEFAULT 1,
    phrase_bank_version TEXT NOT NULL,
    presentation        JSONB NOT NULL,
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (club_id, match_id)
);

-- Index for dashboard queries (latest matches for a club)
CREATE INDEX idx_editorial_played_at
    ON match_editorial_presentations(club_id, played_at DESC);

-- Index for phrase bank version queries (regeneration detection)
CREATE INDEX idx_editorial_phrase_version
    ON match_editorial_presentations(phrase_bank_version);

COMMENT ON TABLE match_editorial_presentations IS
    'Read model for editorial presentations. NOT canonical domain data.';

COMMENT ON COLUMN match_editorial_presentations.phrase_bank_version IS
    'SHA-256 hash of phrase bank state. Used to detect when presentations should be regenerated.';

COMMENT ON COLUMN match_editorial_presentations.presentation IS
    'Complete MatchSummaryPresentation as JSON. Includes all formatted copy and editorial phrases.';

-- Enable Row Level Security
ALTER TABLE match_editorial_presentations ENABLE ROW LEVEL SECURITY;

-- Policy: anon can only SELECT (no INSERT, UPDATE, DELETE)
CREATE POLICY match_editorial_presentations_anon_select_policy
    ON match_editorial_presentations
    FOR SELECT
    TO anon
    USING (true);

-- Verify no other policies exist for anon on this table
-- (INSERT, UPDATE, DELETE implicitly blocked by RLS when no policy exists)

-- Read-only view for Next.js dashboard
-- Exposes only necessary fields, hiding internal versioning details
-- Uses security_invoker to enforce RLS
CREATE OR REPLACE VIEW public.dashboard_editorial_presentations
    WITH (security_invoker = true)
AS
SELECT
    club_id,
    match_id,
    played_at,
    presentation
FROM match_editorial_presentations;

COMMENT ON VIEW public.dashboard_editorial_presentations IS
    'Read-only view for Next.js dashboard. Provides editorial presentations without internal versioning.';

-- Grant ONLY SELECT to anon role (Supabase anonymous access)
-- No INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, or TRIGGER
GRANT SELECT ON public.dashboard_editorial_presentations TO anon;
