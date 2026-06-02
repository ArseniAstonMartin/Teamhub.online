-- V17: automation_rules table — Admin-configurable field auto-fill rules (PRD §16.2; TASK-051).
--
-- The table stores trigger-based rules that auto-fill appointment fields when
-- a dispatcher selects matching trigger values (e.g. locksmith equipment auto-fill).
-- Soft-delete via archived_at; active rules ordered by priority (lower = evaluated first).
--
-- NOTE: 'automation.edit' is already seeded for ADMIN in V4. This migration adds
-- 'automation.read' (reserved for future Dispatcher read-only access per §5.4) and
-- creates the table with RLS.

-- ---------------------------------------------------------------------------
-- 1. Table
-- ---------------------------------------------------------------------------
CREATE TABLE automation_rules (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    name            TEXT        NOT NULL,
    trigger_fields  JSONB,
    match_rules     JSONB,
    actions         JSONB,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    priority        INT         NOT NULL DEFAULT 100,
    archived_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_automation_rules PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- 2. Index — list-active-rules query: tenant + priority where not archived
-- ---------------------------------------------------------------------------
CREATE INDEX idx_automation_rules_tenant_priority
    ON automation_rules (tenant_id, priority)
    WHERE archived_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Row-level security (same NULLIF pattern as V12 jobs table)
-- ---------------------------------------------------------------------------
ALTER TABLE automation_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_rules FORCE ROW LEVEL SECURITY;

CREATE POLICY automation_rules_tenant_isolation ON automation_rules
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- 4. Add automation.read permission (automation.edit already seeded in V4)
--    and assign it to ADMIN only (§5.3: Configure automations — Admin only).
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES ('automation.read')
    ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'automation.read'
WHERE r.tenant_id IS NULL AND r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- 5. Grants — full DML for teamhub_app; DDL stays with the migration user
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON automation_rules TO teamhub_app;
