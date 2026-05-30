-- V4: Flexible role + permission model (PRD §5.2, §5.3, §5.4; TASK-023).
--
-- Replaces scattered `if (role == ADMIN)` checks with a data-driven model:
--   role -> set of permissions, resolved at request time into granted authorities.
-- Built-in roles (ADMIN, DISPATCHER, TECHNICIAN) are global reference data with
-- tenant_id NULL. Adding a future role (e.g. MANAGER, ACCOUNTANT in v2.0 per §5.4)
-- requires only INSERTs here -- zero controller changes.
--
-- RLS decision (deliberate, per TASK-023 brief):
--   roles / permissions / role_permissions are GLOBAL reference data. They carry no
--   per-tenant secrets and must be readable by the application role while the JWT is
--   being authenticated (before any tenant context/GUC is established). We therefore
--   do NOT enable Row-Level Security on these tables and grant SELECT to teamhub_app.
--   TO-DO (PRD §5.4): when v2.0 introduces tenant-CUSTOM roles (non-NULL tenant_id),
--   add an RLS policy on `roles` of the form
--       tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
--   so built-ins stay global while custom roles are tenant-isolated. Human review
--   required before that change.

CREATE TABLE permissions (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL
);

CREATE UNIQUE INDEX ux_permissions_code ON permissions (code);

CREATE TABLE roles (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code      TEXT NOT NULL,
    -- NULL for built-in (global) roles; set for future tenant-custom roles (§5.4).
    tenant_id UUID REFERENCES tenants (id)
);

-- A role code is unique among the global built-ins (tenant_id NULL). Tenant-custom
-- roles in v2.0 will reuse the same code namespace per tenant; that uniqueness will be
-- added alongside the RLS policy noted above.
CREATE UNIQUE INDEX ux_roles_code_builtin ON roles (code) WHERE tenant_id IS NULL;

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

-- ---------------------------------------------------------------------------
-- Seed permissions (the capability matrix from PRD §5.2 and §5.3).
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code) VALUES
    -- Technician-level job capabilities (§5.2 Technician)
    ('job.read.own'),               -- view only own / shared-pool jobs
    ('job.claim'),                  -- claim a job from the shared pool
    ('job.transition'),             -- transition own job (In Progress / Pending / Done pending approval)
    ('job.eta.set'),                -- set ETA (fixed list)
    ('job.note.add'),               -- add notes and photos to own job
    ('team.read.basic'),            -- view basic team info (names, company, shared equipment)
    -- Dispatcher-level capabilities (§5.2 Dispatcher, §5.3)
    ('job.read.tenant'),            -- see all jobs in the tenant
    ('job.create'),                 -- create jobs
    ('job.edit'),                   -- edit / reschedule any job in the tenant
    ('job.pool.move'),              -- move jobs into the shared pool
    ('job.transition.uncancel'),    -- move a job out of Canceled (§5.3: Dispatcher + Admin)
    ('job.transition.completed'),   -- move Done pending approval -> Completed
    ('job.archive'),                -- soft-delete / archive (§5.3: Dispatcher + Admin)
    ('customer.read.tenant'),       -- see all customers in the tenant
    -- Admin-only capabilities (§5.2 Admin, §5.3)
    ('schema.edit'),                -- configure appointment schema (§5.3: Admin only)
    ('automation.edit'),            -- configure automations (§5.3: Admin only)
    ('permission.closejobs.toggle'),-- toggle the tenant Close Jobs permission (§5.3: Admin only)
    ('user.invite'),                -- invite seats
    ('user.deactivate'),            -- deactivate seats
    ('user.role.reassign'),         -- reassign a user's role
    ('report.view.tenant'),         -- view tenant-wide reports
    ('billing.view'),               -- view tenant billing
    ('billing.manage'),             -- change subscription tier / manage billing (§5.3: Admin only)
    ('payment.history.view'),       -- view full payment history (§5.3: Admin only)
    ('job.delete.hard');            -- permanently delete a record (§5.3: Admin only)

-- ---------------------------------------------------------------------------
-- Seed built-in roles (global; tenant_id NULL). §5.1: Admin ⊇ Dispatcher ⊇ Technician.
-- ---------------------------------------------------------------------------
INSERT INTO roles (code, tenant_id) VALUES
    ('TECHNICIAN', NULL),
    ('DISPATCHER', NULL),
    ('ADMIN', NULL);

-- ---------------------------------------------------------------------------
-- Wire role_permissions with the strictly-additive hierarchy (§5.1).
-- TECHNICIAN perms -> granted to TECHNICIAN, DISPATCHER, ADMIN.
-- DISPATCHER-extra perms -> granted to DISPATCHER, ADMIN.
-- ADMIN-extra perms -> granted to ADMIN only.
-- ---------------------------------------------------------------------------

-- TECHNICIAN base set, granted to all three roles (additive superset).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'job.read.own',
    'job.claim',
    'job.transition',
    'job.eta.set',
    'job.note.add',
    'team.read.basic'
)
WHERE r.tenant_id IS NULL AND r.code IN ('TECHNICIAN', 'DISPATCHER', 'ADMIN');

-- DISPATCHER additional set, granted to DISPATCHER and ADMIN.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'job.read.tenant',
    'job.create',
    'job.edit',
    'job.pool.move',
    'job.transition.uncancel',
    'job.transition.completed',
    'job.archive',
    'customer.read.tenant'
)
WHERE r.tenant_id IS NULL AND r.code IN ('DISPATCHER', 'ADMIN');

-- ADMIN-only set.
-- NOTE (PRD §5.2 Technician + §5.3 Close Jobs toggle): a Technician may transition a
-- job to Completed ONLY when the tenant-level "Close Jobs" toggle is enabled. That is a
-- dynamic, per-tenant runtime gate, not a static role grant, so 'job.transition.completed'
-- is NOT seeded for TECHNICIAN here. TO-DO: implement the Close Jobs runtime check in the
-- job transition feature task (PRD §5.3, §10/§5.2).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'schema.edit',
    'automation.edit',
    'permission.closejobs.toggle',
    'user.invite',
    'user.deactivate',
    'user.role.reassign',
    'report.view.tenant',
    'billing.view',
    'billing.manage',
    'payment.history.view',
    'job.delete.hard'
)
WHERE r.tenant_id IS NULL AND r.code = 'ADMIN';

-- Read-only access for the application role; seeding/maintenance is done by the
-- migration (login) user. No RLS on these global reference tables (see header note).
GRANT SELECT ON permissions TO teamhub_app;
GRANT SELECT ON roles TO teamhub_app;
GRANT SELECT ON role_permissions TO teamhub_app;
