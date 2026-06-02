-- V15: Tenant-level close_jobs_enabled toggle + tenant settings permissions
--      (PRD §5.2, §9.3, §13; TASK-053).
--
-- 1. Adds close_jobs_enabled column to tenants (default false — OFF until Admin enables it).
-- 2. Adds tenant settings permissions to the permission matrix:
--    • tenant.settings.read  — ADMIN, DISPATCHER, TECHNICIAN (§5.2: all roles can read settings)
--    • tenant.settings.edit  — ADMIN only (§5.2, §5.3: Admin-only write gate)

-- ---------------------------------------------------------------------------
-- 1. Tenant toggle column
-- ---------------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS close_jobs_enabled boolean NOT NULL DEFAULT false;

-- ---------------------------------------------------------------------------
-- 2. New permission codes
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    ('tenant.settings.read'),   -- read tenant settings (ADMIN, DISPATCHER, TECHNICIAN)
    ('tenant.settings.edit');   -- change tenant settings (ADMIN only)

-- ---------------------------------------------------------------------------
-- 3. Wire role_permissions
-- ---------------------------------------------------------------------------

-- tenant.settings.read → TECHNICIAN, DISPATCHER, ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'tenant.settings.read'
WHERE r.tenant_id IS NULL
  AND r.code IN ('TECHNICIAN', 'DISPATCHER', 'ADMIN');

-- tenant.settings.edit → ADMIN only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'tenant.settings.edit'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';
