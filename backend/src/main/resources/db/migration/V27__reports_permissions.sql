-- V27: Report read permissions for Admin and Dispatcher roles (PRD §18.1, §18.2, TASK-067).
--
-- report.read          — view job count reports; granted to DISPATCHER + ADMIN.
-- report.read.advanced — advanced analytics gate; seeded but NOT wired to any role here.
--                        The Team+ plan check is enforced in QuotaEnforcementService at runtime.

INSERT INTO permissions (code) VALUES
    ('report.read'),            -- basic job-count and avg-time reports (DISPATCHER + ADMIN)
    ('report.read.advanced');   -- per-technician comparisons / custom ranges (Team+ plan gate)

-- Wire report.read to DISPATCHER + ADMIN.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'report.read'
WHERE r.tenant_id IS NULL
  AND r.code IN ('DISPATCHER', 'ADMIN');

-- Wire report.read.advanced to ADMIN only.
-- The actual Team+ plan gate is enforced at runtime in QuotaEnforcementService;
-- this role grant keeps the ADMIN ⊇ all-permissions invariant verified by PermissionResolverIT.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'report.read.advanced'
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';
