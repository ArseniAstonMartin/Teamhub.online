-- V32: Tenant timezone + tenant profile permissions (PRD §5.2, §20.5; TASK-128).
--
-- 1. Adds timezone column to tenants (default America/New_York — US Eastern).
-- 2. Adds tenant profile permissions:
--    • tenant.read  — ADMIN only (GET /api/v1/tenant)
--    • tenant.edit  — ADMIN only (PATCH /api/v1/tenant)

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'America/New_York';

INSERT INTO permissions (code) VALUES
    ('tenant.read'),
    ('tenant.edit');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'tenant.read'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'tenant.edit'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';
