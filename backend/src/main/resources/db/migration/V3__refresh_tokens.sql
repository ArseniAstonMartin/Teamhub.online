-- V3: refresh_tokens table for server-side refresh-token revocation (PRD §6.2, TASK-019).
--
-- Refresh tokens are opaque random strings handed to the client; only their SHA-256
-- hash is stored here, never the raw value. Rotation revokes the presented token and
-- issues a new row. Logout sets revoked_at. RLS follows the same deny-by-default tenant
-- isolation pattern established for users in V2.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id),
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    hash        TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Token lookup on refresh/logout is by hash; the hash is unique per issued token.
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (hash);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Grant table access to the non-superuser application role created in V2.
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO teamhub_app;

-- Enable RLS. FORCE applies the policy even to the table owner.
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy: a row is visible only when the session GUC matches the
-- row's tenant_id. Same deny-by-default semantics as V2 (see V2__rls_users.sql).
-- Note: the login/refresh/logout flows run without a tenant GUC (the login DB user
-- bypasses RLS), so token lookup by hash works before a tenant context is known.
-- Once a tenant context is set (authenticated business requests) RLS scopes rows.
CREATE POLICY tenant_isolation ON refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
