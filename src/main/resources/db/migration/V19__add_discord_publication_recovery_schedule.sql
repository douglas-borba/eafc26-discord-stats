-- Persist the next safe automatic Discord attempt separately from the attempt counter.
-- This allows bounded fast retries followed by a low-frequency reconciliation cadence.
ALTER TABLE discord_publication_state
    ADD COLUMN next_automatic_attempt_at TIMESTAMPTZ,
    ADD COLUMN recovery_attempt_count INTEGER NOT NULL DEFAULT 0;

-- The reconciler selects only due, lightweight work; canonical JSON is loaded after an
-- atomic claim. This partial index keeps that periodic query bounded as the history grows.
CREATE INDEX idx_publication_state_due_recovery
    ON discord_publication_state (next_automatic_attempt_at, updated_at)
    WHERE state IN ('FAILED_TRANSIENT', 'RETRY_EXHAUSTED');

-- Safe rollout: only recently exhausted automatic publications are armed once. Older
-- historical records stay untouched and require an explicit administrative decision, so a
-- deployment cannot replay months of old matches. RETRY_EXHAUSTED is produced only after a
-- proven non-delivery under the existing automatic policy.
UPDATE discord_publication_state
SET next_automatic_attempt_at = GREATEST(COALESCE(last_attempt_at, updated_at), now()) + INTERVAL '30 minutes',
    recovery_attempt_count = 0
WHERE state = 'RETRY_EXHAUSTED'
  AND next_automatic_attempt_at IS NULL
  AND COALESCE(last_attempt_at, updated_at) >= now() - INTERVAL '48 hours';

-- Recent legacy transient records retain the current bounded backoff after deployment.
UPDATE discord_publication_state
SET next_automatic_attempt_at = CASE attempt_count
    WHEN 0 THEN now()
    WHEN 1 THEN last_attempt_at + INTERVAL '1 minute'
    WHEN 2 THEN last_attempt_at + INTERVAL '2 minutes'
    WHEN 3 THEN last_attempt_at + INTERVAL '5 minutes'
    WHEN 4 THEN last_attempt_at + INTERVAL '15 minutes'
    ELSE NULL
END
WHERE state = 'FAILED_TRANSIENT'
  AND next_automatic_attempt_at IS NULL
  AND COALESCE(last_attempt_at, updated_at) >= now() - INTERVAL '48 hours';
