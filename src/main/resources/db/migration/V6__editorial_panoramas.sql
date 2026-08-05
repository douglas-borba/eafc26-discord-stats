-- V6: AI-generated editorial panoramas
--
-- Stores LLM-generated panorama narratives for the overview page.
-- Generated when the set of recent matches changes, not on every page load.
-- Idempotency key prevents duplicate LLM calls for the same context.

CREATE TABLE IF NOT EXISTS editorial_panoramas (
    id                  BIGSERIAL PRIMARY KEY,
    club_id             TEXT NOT NULL,
    context_key         TEXT NOT NULL,
    match_ids           TEXT[] NOT NULL,
    narrative           TEXT,
    provider            TEXT,
    model               TEXT,
    prompt_version      TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'pending',
    error_category      TEXT,
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_panorama_context UNIQUE (club_id, context_key)
);

CREATE INDEX idx_panoramas_club_status ON editorial_panoramas (club_id, status);
CREATE INDEX idx_panoramas_club_latest ON editorial_panoramas (club_id, generated_at DESC);

COMMENT ON TABLE editorial_panoramas IS
    'AI-generated editorial panorama narratives. One per unique match context.';
COMMENT ON COLUMN editorial_panoramas.context_key IS
    'Idempotency key: SHA-256 of club_id + ordered match IDs + prompt_version + model.';
COMMENT ON COLUMN editorial_panoramas.status IS
    'pending | success | failed';

-- Enable RLS
ALTER TABLE editorial_panoramas ENABLE ROW LEVEL SECURITY;

CREATE POLICY panoramas_anon_select ON editorial_panoramas
    FOR SELECT TO anon USING (true);

GRANT SELECT ON editorial_panoramas TO anon;

-- Read-only view for Next.js dashboard
CREATE OR REPLACE VIEW public.dashboard_panoramas
    WITH (security_invoker = true)
AS
SELECT
    club_id,
    narrative,
    status,
    generated_at
FROM editorial_panoramas
WHERE status = 'success' AND narrative IS NOT NULL;

COMMENT ON VIEW public.dashboard_panoramas IS
    'Read-only view exposing successful AI panoramas for the Next.js dashboard.';

GRANT SELECT ON public.dashboard_panoramas TO anon;
