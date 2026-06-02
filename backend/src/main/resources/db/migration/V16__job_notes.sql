-- V16: job_notes table — append-only notes on jobs (PRD §13.2, §13.4; TASK-048).
--
-- Notes are immutable: GRANT includes only SELECT + INSERT; no UPDATE or DELETE.
-- RLS policy isolates rows by tenant_id (same NULLIF pattern as V12).
-- Index on (job_id, created_at DESC) supports the list-by-job query.
--
-- NOTE: The permission 'job.note.add' and its role assignments were already seeded
-- in V4 (the baseline permission matrix). No new permission rows are required here.

-- ---------------------------------------------------------------------------
-- 1. Table
-- ---------------------------------------------------------------------------
CREATE TABLE job_notes (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_id          UUID        NOT NULL REFERENCES jobs(id),
    tenant_id       UUID        NOT NULL,
    author_user_id  UUID        NOT NULL,
    body            TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_job_notes PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- 2. Index
-- ---------------------------------------------------------------------------
CREATE INDEX idx_job_notes_job_id_created_at
    ON job_notes (job_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 3. Row-level security (same NULLIF pattern as V12 jobs table)
-- ---------------------------------------------------------------------------
ALTER TABLE job_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_notes FORCE ROW LEVEL SECURITY;

CREATE POLICY job_notes_tenant_isolation ON job_notes
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- 4. Grants — SELECT + INSERT only (immutability enforced at DB level)
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT ON job_notes TO teamhub_app;
