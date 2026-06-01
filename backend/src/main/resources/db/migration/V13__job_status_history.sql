-- V13: job status history table (TASK-043)
-- Stores an immutable audit trail of every job status transition.

CREATE TABLE job_status_history (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    job_id         UUID         NOT NULL REFERENCES jobs (id),
    from_status    TEXT,
    to_status      TEXT         NOT NULL,
    actor_user_id  UUID,
    actor_role     TEXT,
    transition_note TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_job_status_history PRIMARY KEY (id)
);

CREATE INDEX idx_job_status_history_job_created
    ON job_status_history (job_id, created_at);

ALTER TABLE job_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_status_history FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON job_status_history
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', TRUE), '')::uuid);

GRANT SELECT, INSERT ON job_status_history TO teamhub_app;
