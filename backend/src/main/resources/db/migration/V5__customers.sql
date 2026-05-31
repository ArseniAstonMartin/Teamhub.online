-- V5: customers table with PostGIS geography(Point,4326), RLS tenant isolation (PRD §11.1, TASK-036).
--
-- Follows the same RLS pattern as V2 (users) and V3 (refresh_tokens):
--   enable RLS + FORCE RLS, deny-by-default policy using NULLIF(current_setting(...), '').

CREATE TABLE customers (
    id           uuid                  NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    uuid                  NOT NULL REFERENCES tenants (id),
    name         text                  NOT NULL,
    company_name text,
    phone        text                  NOT NULL, -- E.164 format
    email        text,
    address_line text                  NOT NULL,
    city         text                  NOT NULL,
    state        text                  NOT NULL,
    zip          text                  NOT NULL,
    location     geography(Point,4326) NOT NULL,
    created_at   timestamptz           NOT NULL DEFAULT now(),
    updated_at   timestamptz           NOT NULL DEFAULT now(),
    archived_at  timestamptz,
    CONSTRAINT customers_pkey PRIMARY KEY (id)
);

-- GIST spatial index enables ST_DWithin, ST_Within, and other spatial range queries
-- to use the index rather than a full table scan.
CREATE INDEX customers_location_gist ON customers USING GIST (location);

-- Grant table access to the non-superuser application role (created in V2).
GRANT SELECT, INSERT, UPDATE, DELETE ON customers TO teamhub_app;

-- Enable RLS (deny-by-default).  FORCE ensures the policy also applies when the
-- table owner queries the table directly.
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy — identical predicate to V2/V3:
--   GUC absent            → current_setting returns NULL  → NULLIF(NULL,'')  = NULL
--   GUC RESET (empty str) → current_setting returns ''    → NULLIF('','')    = NULL
-- In both cases tenant_id = NULL is treated as FALSE by RLS → deny.
CREATE POLICY tenant_isolation ON customers
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
