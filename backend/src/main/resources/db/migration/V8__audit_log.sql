-- V8: audit_log table for append-only audit trail (PRD §19).
--
-- The table is INSERT-only by design: the app_audit_writer role has only INSERT
-- (no UPDATE, no DELETE). The app's normal role (teamhub_app) has only SELECT.
-- The Postgres login user (postgres) used by Flyway retains full access for
-- migrations/maintenance. No application code may UPDATE or DELETE rows.

-- Create the audit-writer role if it does not exist.
-- This role has no login and never connects directly; it is used via SET LOCAL ROLE.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_audit_writer') THEN
        CREATE ROLE app_audit_writer;
    END IF;
END $$;

-- Allow the migration/login user to SET ROLE app_audit_writer.
DO $$
BEGIN
    EXECUTE format('GRANT app_audit_writer TO %I', current_user);
END $$;

CREATE TABLE audit_log (
    id            uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES tenants(id),
    actor_user_id uuid                 REFERENCES users(id),
    action        text        NOT NULL,
    resource_type text        NOT NULL,
    resource_id   uuid,
    before_state  jsonb,
    after_state   jsonb,
    ip_address    inet,
    user_agent    text,
    occurred_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

-- Partition-friendly index for tenant-scoped time-ordered reads.
CREATE INDEX idx_audit_log_tenant_occurred ON audit_log(tenant_id, occurred_at DESC);

-- RLS: same ENABLE + FORCE + tenant_isolation pattern as V2, V5, V7.
-- The SELECT policy also applies to the audit writer; it cannot read rows from
-- other tenants, and writes are filtered by the INSERT-only GRANT below.
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON audit_log
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- teamhub_app (normal app role): SELECT only — no INSERT, UPDATE, or DELETE.
GRANT SELECT ON audit_log TO teamhub_app;

-- app_audit_writer: INSERT only — no SELECT, UPDATE, or DELETE.
GRANT INSERT ON audit_log TO app_audit_writer;
