-- V2: PostgreSQL Row-Level Security on the users table (PRD §7.2).
--
-- Creates a non-superuser application role (teamhub_app) that is always subject
-- to RLS, then enables a deny-by-default tenant isolation policy on users.
-- Every future business table must follow this same pattern via a new migration.

-- Create the application DB role if it does not already exist.
-- This role has no login; the app connects as its DB user and uses SET ROLE.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'teamhub_app') THEN
        CREATE ROLE teamhub_app;
    END IF;
END $$;

-- Grant schema and table access to the application role.
GRANT USAGE ON SCHEMA public TO teamhub_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenants TO teamhub_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON users TO teamhub_app;

-- Enable RLS on users.
-- FORCE ensures the policy also applies when the table owner queries the table.
-- Superusers with BYPASSRLS still bypass the policy; those do not exist in production.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy: a row is visible only when the session GUC matches
-- the row's tenant_id.
-- current_setting('app.current_tenant_id', true) returns NULL (not an error)
-- when the GUC is absent, making the policy deny-by-default with no context set.
-- NULLIF handles two cases:
--   * GUC never set    → current_setting returns NULL  → NULLIF(NULL, '')  = NULL
--   * GUC RESET/empty  → current_setting returns ''    → NULLIF('', '')    = NULL
-- In both cases tenant_id = NULL evaluates to NULL (treated as FALSE by RLS → deny).
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
