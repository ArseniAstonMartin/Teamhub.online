-- V21: job_photos table, RLS tenant isolation, and photo.upload permission (PRD §14.2, §14.3, TASK-049).
--
-- 1. Creates job_photos to record confirmed photo uploads with EXIF metadata fields.
-- 2. Enables tenant-isolation RLS (same NULLIF pattern as other tenant tables).
-- 3. Adds 'photo.upload' permission; assigns it to ADMIN, DISPATCHER, TECHNICIAN.

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------
CREATE TABLE job_photos (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_id       UUID        NOT NULL REFERENCES jobs(id),
    tenant_id    UUID        NOT NULL,
    uploaded_by  UUID        NOT NULL,
    object_key   TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    width        INT,
    height       INT,
    taken_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_job_photos PRIMARY KEY (id)
);

CREATE INDEX idx_job_photos_job_id ON job_photos(job_id);
CREATE INDEX idx_job_photos_tenant_id ON job_photos(tenant_id);

-- ---------------------------------------------------------------------------
-- RLS — tenant isolation (same NULLIF pattern as V2/V5/V12)
-- ---------------------------------------------------------------------------
ALTER TABLE job_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_photos FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON job_photos
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

GRANT SELECT, INSERT ON job_photos TO teamhub_app;

-- ---------------------------------------------------------------------------
-- New permission
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code)
VALUES ('photo.upload')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Grant photo.upload to ADMIN, DISPATCHER, TECHNICIAN
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'photo.upload'
WHERE r.tenant_id IS NULL
  AND r.code IN ('ADMIN', 'DISPATCHER', 'TECHNICIAN')
ON CONFLICT DO NOTHING;
