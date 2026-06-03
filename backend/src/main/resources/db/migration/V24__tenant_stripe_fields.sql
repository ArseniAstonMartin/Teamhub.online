-- V24: Stripe billing fields for tenants + billing.manage permission (TASK-061, PRD §17.2).
--
-- stripe_customer_id and plan already exist from V1 baseline (IF NOT EXISTS is safe).
-- stripe_subscription_id is new — populated by the TASK-062 webhook handler on checkout.session.completed.
-- billing.manage permission gates POST /api/v1/billing/checkout/{priceKey} (ADMIN only).

-- ---------------------------------------------------------------------------
-- Schema changes
-- ---------------------------------------------------------------------------
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS stripe_customer_id TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS stripe_subscription_id TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS plan TEXT NOT NULL DEFAULT 'FREE';

-- ---------------------------------------------------------------------------
-- billing.manage permission
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES ('billing.manage')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Wire role_permissions — ADMIN only
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'billing.manage'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
