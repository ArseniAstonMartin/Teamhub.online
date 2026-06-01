-- V12: jobs table with all PRD §22 columns, PostGIS geography, JSONB custom_fields,
--      optimistic-locking version column, RLS tenant isolation (PRD §9, §22, TASK-042).

CREATE TABLE jobs (
    id                      uuid                  NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               uuid                  NOT NULL REFERENCES tenants(id),
    customer_id             uuid                  NOT NULL REFERENCES customers(id),
    assigned_technician_id  uuid                  REFERENCES users(id),
    appointment_schema_id   uuid                  REFERENCES appointment_schemas(id),
    status                  text                  NOT NULL,
    in_progress_substatus   text,
    pending_reason          text,
    cancellation_reason     text,
    custom_fields           jsonb                 NOT NULL DEFAULT '{}'::jsonb,
    location                geography(Point,4326),
    scheduled_start         timestamptz,
    scheduled_end           timestamptz,
    actual_start            timestamptz,
    actual_end              timestamptz,
    version                 int                   NOT NULL DEFAULT 0,
    created_at              timestamptz           NOT NULL DEFAULT now(),
    updated_at              timestamptz           NOT NULL DEFAULT now(),
    CONSTRAINT pk_jobs PRIMARY KEY (id)
);

-- Query patterns: list jobs by tenant + status + date range, and by assigned technician.
CREATE INDEX idx_jobs_tenant_status_scheduled_start
    ON jobs(tenant_id, status, scheduled_start);

CREATE INDEX idx_jobs_tenant_technician
    ON jobs(tenant_id, assigned_technician_id);

-- Enable RLS (deny-by-default). FORCE ensures the policy applies to the table owner too.
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy — same NULLIF pattern as V2/V5/V7:
--   GUC absent or empty string → NULLIF returns NULL → tenant_id = NULL → FALSE → deny.
CREATE POLICY tenant_isolation ON jobs
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON jobs TO teamhub_app;
