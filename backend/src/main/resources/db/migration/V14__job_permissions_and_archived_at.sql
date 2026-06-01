-- V14: Job CRUD permissions + archived_at column on jobs (PRD §5.2, §5.3, §9, TASK-044).
--
-- 1. Adds job.delete (soft-archive) and job.hard_delete (physical delete) permissions.
--    Most job permissions were seeded in V4; these two new codes map cleanly to the two
--    delete endpoints introduced in TASK-044.
--    • job.delete      — soft-archive: DISPATCHER + ADMIN (§5.3: Dispatcher + Admin)
--    • job.hard_delete — physical delete: ADMIN only (§5.3: Admin only)
--
-- 2. Adds archived_at column to jobs — same soft-delete pattern as customers (V5).

-- ---------------------------------------------------------------------------
-- New permission codes
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    ('job.delete'),        -- soft-archive a job (DISPATCHER + ADMIN)
    ('job.hard_delete');   -- permanently delete a job (ADMIN only)

-- ---------------------------------------------------------------------------
-- Wire role_permissions
-- ---------------------------------------------------------------------------

-- job.delete → DISPATCHER + ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'job.delete'
WHERE r.tenant_id IS NULL
  AND r.code IN ('DISPATCHER', 'ADMIN');

-- job.hard_delete → ADMIN only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'job.hard_delete'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- Soft-delete support for jobs
-- ---------------------------------------------------------------------------
ALTER TABLE jobs ADD COLUMN archived_at timestamptz;

-- Partial index: list queries filter on archived_at IS NULL.
CREATE INDEX idx_jobs_tenant_active
    ON jobs (tenant_id, status, scheduled_start)
    WHERE archived_at IS NULL;

-- Grant DELETE so that the hard-delete endpoint can physically remove a row.
-- V12 granted only SELECT, INSERT, UPDATE; ADMIN-only hard-delete requires DELETE.
GRANT DELETE ON jobs TO teamhub_app;
