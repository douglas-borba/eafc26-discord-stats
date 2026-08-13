-- V15 was released with a three-match counter. Existing expired previews retain
-- their snapshot and become normal TRIAL records; neither state is monitored.
UPDATE monitored_clubs
SET access_status = 'TRIAL', monitoring_enabled = FALSE
WHERE access_status = 'TRIAL_EXPIRED';

ALTER TABLE monitored_clubs DROP CONSTRAINT IF EXISTS monitored_clubs_access_status_check;
ALTER TABLE monitored_clubs
    ADD CONSTRAINT monitored_clubs_access_status_check CHECK (access_status IN ('TRIAL', 'ACTIVE'));
ALTER TABLE monitored_clubs DROP COLUMN IF EXISTS trial_limit;
ALTER TABLE monitored_clubs DROP COLUMN IF EXISTS trial_started_at;

DROP TABLE IF EXISTS trial_match_consumptions;
