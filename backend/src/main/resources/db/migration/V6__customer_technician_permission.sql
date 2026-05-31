-- V6: Grant customer.read.tenant to TECHNICIAN role (PRD §11.2, TASK-037).
--
-- PRD §11.2: Technicians see the full customer list but phone numbers are masked for customers
-- not linked to a job they have claimed.  The masking is enforced at the application layer
-- (CustomerController.isTechnician) — this migration only grants the SELECT permission so that
-- the security filter chain permits TECHNICIAN tokens to reach the customer endpoints.
--
-- Context: V4__roles_permissions.sql placed customer.read.tenant in the DISPATCHER-extra set
-- (DISPATCHER + ADMIN).  TASK-037 adds it to TECHNICIAN too; the phone-masking logic is the
-- gate that preserves the PRD §11.2 privacy guarantee.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'customer.read.tenant'
WHERE r.tenant_id IS NULL
  AND r.code = 'TECHNICIAN';
