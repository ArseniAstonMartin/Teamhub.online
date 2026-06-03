-- V26: Per-tenant quota period state for threshold-event firing (TASK-065, PRD §17.3, §22.5.7).
--
-- tenant_quota_state: tracks job count and threshold-fired flags per billing period per tenant.
-- Backend-only maintenance table — no RLS needed.

CREATE TABLE IF NOT EXISTS tenant_quota_state (
    tenant_id         UUID        NOT NULL,
    period_start      TIMESTAMPTZ NOT NULL,
    job_count         INT         NOT NULL DEFAULT 0,
    threshold80_fired BOOLEAN     NOT NULL DEFAULT false,
    threshold100_fired BOOLEAN    NOT NULL DEFAULT false,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tenant_quota_state_pkey PRIMARY KEY (tenant_id),
    CONSTRAINT tenant_quota_state_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

GRANT SELECT, INSERT, UPDATE ON tenant_quota_state TO teamhub_app;
