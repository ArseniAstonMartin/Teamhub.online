-- V11: ShedLock distributed-lock table (TASK-033, PRD §22.5.4).
--
-- ShedLock uses this table to guarantee that @Scheduled methods annotated with
-- @SchedulerLock execute on at most one node at a time.  The lock provider is
-- JdbcTemplateLockProvider configured in SchedulingConfig.
--
-- Column notes:
--   name        — unique lock name (matches the name attribute of @SchedulerLock)
--   lock_until  — UTC timestamp until which the lock is considered held
--   locked_at   — UTC timestamp when the lock was last acquired
--   locked_by   — hostname / instance identifier of the lock holder

CREATE TABLE shedlock (
    name        VARCHAR(64)      NOT NULL,
    lock_until  TIMESTAMP(3)     NOT NULL,
    locked_at   TIMESTAMP(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by   VARCHAR(255)     NOT NULL,

    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- The application user must be able to INSERT a new lock row and UPDATE an
-- existing one (ShedLock uses an upsert strategy internally).
GRANT SELECT, INSERT, UPDATE ON shedlock TO teamhub_app;
