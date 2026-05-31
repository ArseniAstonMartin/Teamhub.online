-- V7: appointment_schemas table for versioned, append-only field definitions (PRD §8.2).
--
-- The table is append-only: old versions are never deleted so that historical job records
-- can still reference and validate against their schema_version. When a new version is
-- published the application sets the previous active row's is_active = false (single-active
-- semantics per tenant + name pair, enforced by SchemaService).

CREATE TABLE appointment_schemas (
    id                uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES tenants(id),
    name              text        NOT NULL,
    version           int         NOT NULL,
    is_active         boolean     NOT NULL DEFAULT true,
    field_definitions jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_appointment_schemas PRIMARY KEY (id),
    -- Each (tenant, schema name, version) is globally unique.
    CONSTRAINT uq_appointment_schemas_tenant_name_version UNIQUE (tenant_id, name, version)
);

-- Covers the active-schema lookup: findByTenantIdAndNameAndIsActiveTrue
CREATE INDEX idx_appointment_schemas_tenant_name_active
    ON appointment_schemas(tenant_id, name, is_active);

-- RLS: same ENABLE + FORCE + tenant_isolation policy pattern as V2 and V5.
ALTER TABLE appointment_schemas ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment_schemas FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON appointment_schemas
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON appointment_schemas TO teamhub_app;
