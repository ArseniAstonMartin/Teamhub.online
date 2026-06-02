-- V19: Tenant invite table and permissions (PRD §6.5, TASK-054).
--
-- Admins invite users by email + role. A signed token is emailed; the invitee
-- sets a password and is created as a tenant user upon acceptance.
--
-- No RLS is applied: the accept endpoint is public (no JWT / no tenant context),
-- so the invite must be readable by the login DB user — same pattern as V18
-- (password_reset_tokens).

CREATE TABLE invites (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    email        TEXT        NOT NULL,
    role         TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    accepted_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resent_at    TIMESTAMPTZ
);

CREATE INDEX idx_invites_token_hash
    ON invites (token_hash);

CREATE INDEX idx_invites_tenant_email
    ON invites (tenant_id, email);

GRANT SELECT, INSERT, UPDATE ON invites TO teamhub_app;

-- ---------------------------------------------------------------------------
-- New permissions: invite.create (send invite) and invite.manage (resend/cancel).
-- Both are Admin-only per PRD §6.5.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    ('invite.create'),
    ('invite.manage');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('invite.create', 'invite.manage')
WHERE r.tenant_id IS NULL AND r.code = 'ADMIN';
