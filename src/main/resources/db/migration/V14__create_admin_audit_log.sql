-- Administrative actions are durable audit records, separate from operational diagnostics.
CREATE TABLE admin_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    admin_email TEXT NOT NULL,
    action      TEXT NOT NULL,
    club_id     TEXT,
    match_id    TEXT,
    result      TEXT NOT NULL,
    error_code  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_club_created ON admin_audit_log(club_id, created_at DESC);
CREATE INDEX idx_admin_audit_log_action_created ON admin_audit_log(action, created_at DESC);
