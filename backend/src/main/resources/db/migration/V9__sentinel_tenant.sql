-- V9: Sentinel tenant for pre-authentication audit events (TASK-028).
--
-- Some audit events (e.g. auth.login_failed with an unknown email) occur before any tenant context
-- is established and before the actor can be linked to a real tenant row.  The audit_log table
-- requires a NOT NULL tenant_id with an FK reference to tenants(id).
--
-- Design choice: rather than making tenant_id nullable (which would weaken the schema for all
-- other events) or silently dropping failed-login audit rows (which would violate PRD §19.2), we
-- insert a single sentinel row whose UUID is the all-zeros UUID.  This sentinel is never exposed
-- to real users or included in tenant-scoped audit exports.
--
-- Lookup strategy in AuthService:
--  1. If the email maps to a real user, use that user's tenant_id.
--  2. If the email is unknown (not in the users table), fall back to this sentinel UUID.
INSERT INTO tenants (id, name, plan)
VALUES ('00000000-0000-0000-0000-000000000000', '_system_sentinel', 'FREE')
ON CONFLICT (id) DO NOTHING;
