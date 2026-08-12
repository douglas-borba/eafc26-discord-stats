ALTER TABLE monitored_clubs
    ADD COLUMN access_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN trial_limit INTEGER,
    ADD COLUMN trial_started_at TIMESTAMPTZ,
    ADD CONSTRAINT monitored_clubs_access_status_check CHECK (access_status IN ('TRIAL', 'ACTIVE', 'TRIAL_EXPIRED'));

CREATE TABLE trial_requests (
    id BIGSERIAL PRIMARY KEY,
    club_name VARCHAR(160) NOT NULL,
    requester_name VARCHAR(160) NOT NULL,
    contact VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    club_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    CONSTRAINT trial_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_trial_requests_status_created ON trial_requests(status, created_at DESC);

CREATE TABLE trial_match_consumptions (
    club_id VARCHAR(255) NOT NULL REFERENCES monitored_clubs(club_id),
    match_id VARCHAR(255) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (club_id, match_id)
);
