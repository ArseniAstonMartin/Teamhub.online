-- V22: multipart_uploads table for resumable large-file uploads (PRD §14.4, TASK-050).
--
-- 1. Creates multipart_uploads to track S3 multipart upload sessions per job/tenant.
-- 2. Enables tenant-isolation RLS (same NULLIF pattern as other tenant tables).
-- 3. Grants SELECT, INSERT, UPDATE to teamhub_app (no DELETE — rows are soft-aborted).

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------
CREATE TABLE multipart_uploads (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_id        UUID        NOT NULL REFERENCES jobs(id),
    tenant_id     UUID        NOT NULL,
    initiated_by  UUID        NOT NULL,
    s3_upload_id  TEXT        NOT NULL,
    object_key    TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'IN_PROGRESS',
    part_count    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_multipart_uploads PRIMARY KEY (id),
    CONSTRAINT chk_multipart_uploads_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABORTED'))
);

CREATE INDEX idx_multipart_uploads_job_id ON multipart_uploads(job_id);
CREATE INDEX idx_multipart_uploads_status_created_at ON multipart_uploads(status, created_at);

-- ---------------------------------------------------------------------------
-- RLS — tenant isolation (same NULLIF pattern as V2/V5/V12/V21)
-- ---------------------------------------------------------------------------
ALTER TABLE multipart_uploads ENABLE ROW LEVEL SECURITY;
ALTER TABLE multipart_uploads FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON multipart_uploads
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON multipart_uploads TO teamhub_app;
