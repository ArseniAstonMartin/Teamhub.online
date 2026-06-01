-- V10: Eventing infrastructure tables (PRD §22, §22.5, TASK-029).
--
-- Three tables:
--   outbox_events     — transactional outbox; relay reads unprocessed rows and XADDs to Redis Streams.
--   processed_events  — idempotency de-dup guard; partitioned by month per PRD §22.5.6.
--   delayed_jobs      — time-scheduled work items backed by a Redis sorted set.
--
-- All three tables carry tenant_id and follow the same RLS pattern as V2/V5/V7/V8:
--   ENABLE ROW LEVEL SECURITY + FORCE ROW LEVEL SECURITY + tenant_isolation policy
--   using NULLIF(current_setting('app.current_tenant_id', true), '')::uuid.
--
-- Partitioning note (processed_events):
--   PostgreSQL declarative RANGE partitioning on processed_at.  The partition key column
--   must be part of every unique constraint, so the PRIMARY KEY becomes
--   (event_id, consumer_group, processed_at).  Old partitions are dropped after the
--   corresponding events age out of the Redis Stream (default 30 days), per PRD §22.5.6.

-- ============================================================
-- 1. outbox_events
-- ============================================================

CREATE TABLE outbox_events (
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES tenants (id),
    event_type     text        NOT NULL,
    payload        jsonb       NOT NULL,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    processed_at   timestamptz,
    attempt_count  int         NOT NULL DEFAULT 0,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- Relay-scan index: unprocessed rows bubble to the top (NULLS FIRST), then ordered by
-- creation time so the relay processes events in arrival order (PRD §22.5.3).
CREATE INDEX idx_outbox_events_relay
    ON outbox_events (processed_at NULLS FIRST, created_at);

-- teamhub_app needs SELECT (relay reads) + INSERT (producers) + UPDATE (relay marks processed_at).
GRANT SELECT, INSERT, UPDATE ON outbox_events TO teamhub_app;

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;

-- Deny-by-default policy: GUC absent/RESET → NULL → tenant_id = NULL → false → deny.
CREATE POLICY tenant_isolation ON outbox_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- ============================================================
-- 2. processed_events  (partitioned by month on processed_at)
-- ============================================================

-- Parent table — no rows stored here directly; all writes go to a child partition.
CREATE TABLE processed_events (
    event_id       uuid        NOT NULL,
    consumer_group text        NOT NULL,
    processed_at   timestamptz NOT NULL DEFAULT now(),
    tenant_id      uuid        NOT NULL REFERENCES tenants (id),

    -- partition key (processed_at) must be part of the PK for declarative partitioning.
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group, processed_at)
) PARTITION BY RANGE (processed_at);

-- Current-month partition (June 2026).  Future partitions are created by a scheduled job or
-- the partition-management task before each month begins.
CREATE TABLE processed_events_2026_06
    PARTITION OF processed_events
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

-- teamhub_app needs INSERT (consumer writes de-dup guard) + SELECT (idempotency check).
GRANT SELECT, INSERT ON processed_events TO teamhub_app;

ALTER TABLE processed_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_events FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON processed_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- ============================================================
-- 3. delayed_jobs
-- ============================================================

CREATE TABLE delayed_jobs (
    id            uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES tenants (id),
    kind          text        NOT NULL,
    payload       jsonb       NOT NULL,
    run_at        timestamptz NOT NULL,
    status        text        NOT NULL DEFAULT 'PENDING',
    attempt_count int         NOT NULL DEFAULT 0,

    CONSTRAINT pk_delayed_jobs PRIMARY KEY (id)
);

-- Dispatcher-scan index: filter by status, order by run_at so due entries surface first
-- (PRD §22.5.4).
CREATE INDEX idx_delayed_jobs_status_run_at
    ON delayed_jobs (status, run_at);

-- teamhub_app needs full DML: INSERT (schedule), SELECT (dispatcher read), UPDATE (status transitions).
GRANT SELECT, INSERT, UPDATE ON delayed_jobs TO teamhub_app;

ALTER TABLE delayed_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE delayed_jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON delayed_jobs
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
