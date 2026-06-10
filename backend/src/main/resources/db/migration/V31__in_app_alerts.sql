-- V31: In-app alert records for Admin escalation banners (PRD §28, TASK-118).
--
-- Each row represents a pending or dismissed in-app banner for an Admin user.
-- Created by EscalateUnclaimedJobHandler when a job is still SUBMITTED after
-- the escalation window; dismissed via POST /api/v1/alerts/{id}/dismiss.
--
-- No RLS: access is enforced at the service layer by matching user_id to the
-- authenticated principal (same pattern as device_tokens).

CREATE TABLE in_app_alerts (
  id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  tenant_id   UUID        NOT NULL REFERENCES tenants(id),
  user_id     UUID        NOT NULL,
  job_id      UUID        REFERENCES jobs(id),
  kind        TEXT        NOT NULL DEFAULT 'JOB_NOT_HANDLED',
  dismissed   BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_in_app_alerts_user_pending ON in_app_alerts(user_id, dismissed) WHERE dismissed = FALSE;

GRANT SELECT, INSERT, UPDATE, DELETE ON in_app_alerts TO teamhub_app;

-- alert.read permission — read and dismiss in-app alert banners; ADMIN only (PRD §28).
INSERT INTO permissions (code) VALUES ('alert.read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'alert.read'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';
