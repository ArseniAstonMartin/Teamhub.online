-- V20: User-management permissions for Admin (PRD §5.2, TASK-055).
--
-- 1. Adds deactivated_at column to users (NULL = active, non-NULL = deactivated timestamp).
-- 2. Seeds user.list, user.reactivate, user.role.change permissions (new).
--    user.deactivate already exists from V4; ON CONFLICT DO NOTHING is safe for all four.
-- 3. Assigns all four permissions to ADMIN only.

-- ---------------------------------------------------------------------------
-- Schema change
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ;

-- Partial index: finding active users for the "last admin" guard is a hot path.
CREATE INDEX IF NOT EXISTS idx_users_tenant_admin_active
    ON users (tenant_id)
    WHERE role = 'ADMIN' AND deactivated_at IS NULL;

-- ---------------------------------------------------------------------------
-- New permission codes (ON CONFLICT DO NOTHING in case of replay / partial run)
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    ('user.list'),
    ('user.deactivate'),
    ('user.reactivate'),
    ('user.role.change')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Wire role_permissions — ADMIN only (additive to V4 grants)
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN (
    'user.list',
    'user.deactivate',
    'user.reactivate',
    'user.role.change'
    )
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
