-- V34: Global staff_users table for Teamhub internal console auth (PRD §6.4, TASK-131).
--
-- Staff accounts are global (no tenant_id, no RLS). Used exclusively by /api/v1/internal/**.

CREATE TABLE staff_users (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email         CITEXT      NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    name          TEXT,
    role          TEXT        NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE ON staff_users TO teamhub_app;

-- Root staff for local development. Plaintext password documented in infra/runbooks/local-dev.md.
-- TO-DO: human must rotate dev@teamhub.online credentials before production (PRD §6.4).
INSERT INTO staff_users (id, email, password_hash, name, role, is_active)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'dev@teamhub.online',
    '$2b$10$gCPOsDg8lMffuXx9M/8q3.UOy9Efl0U5Xyj8R/gx9JubaPafYocTu',
    'Teamhub Dev',
    'TEAMHUB_STAFF',
    true
);
