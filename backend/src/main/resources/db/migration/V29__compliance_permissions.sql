-- V29: CCPA compliance permissions for data export and erasure (PRD §24.5, TASK-071).
--
-- compliance.export — download a ZIP of all tenant data; ADMIN only.
-- compliance.erase  — anonymise a customer's PII; ADMIN only.
--
-- Both operations are sensitive, irreversible, and audit-logged. They are
-- deliberately restricted to the ADMIN role in accordance with PRD §24.5.

INSERT INTO permissions (code) VALUES
    ('compliance.export'),
    ('compliance.erase');

-- Wire both permissions to ADMIN only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('compliance.export', 'compliance.erase')
WHERE r.tenant_id IS NULL
  AND r.code = 'ADMIN';
