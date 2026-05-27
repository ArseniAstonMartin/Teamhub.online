-- V1 baseline: tenants and users tables.
-- citext provides case-insensitive text for email comparison.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE tenants (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 TEXT        NOT NULL,
    plan                 TEXT        NOT NULL DEFAULT 'FREE',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ,
    stripe_customer_id   TEXT
);

CREATE TABLE users (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants (id),
    email          CITEXT      UNIQUE,
    password_hash  TEXT,
    role           TEXT        NOT NULL,
    name           TEXT,
    is_active      BOOLEAN     NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ DEFAULT now()
);
