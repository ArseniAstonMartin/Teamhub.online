-- V28: Materialized views for report endpoints (TASK-068, PRD §18.2).
--
-- NOTE ON ROW-LEVEL SECURITY: Postgres does not support RLS policies on materialized views.
-- Tenant isolation is enforced by the application — every query against these views MUST include
-- a WHERE tenant_id = ? predicate. The unique indexes below also encode the tenant_id as the
-- leading column, which doubles as a partial performance hint for such filters.

-- ---------------------------------------------------------------------------
-- mv_jobs_daily: job counts per tenant / calendar-day / status
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_jobs_daily AS
SELECT tenant_id,
       date_trunc('day', scheduled_start)::date AS day,
       status,
       COUNT(*) AS job_count
FROM jobs
WHERE archived_at IS NULL
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX mv_jobs_daily_pk ON mv_jobs_daily(tenant_id, day, status);

-- ---------------------------------------------------------------------------
-- mv_jobs_weekly: job counts per tenant / ISO-week start / status
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_jobs_weekly AS
SELECT tenant_id,
       date_trunc('week', scheduled_start)::date AS week_start,
       status,
       COUNT(*) AS job_count
FROM jobs
WHERE archived_at IS NULL
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX mv_jobs_weekly_pk ON mv_jobs_weekly(tenant_id, week_start, status);

-- ---------------------------------------------------------------------------
-- mv_jobs_monthly: job counts per tenant / month start / status
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_jobs_monthly AS
SELECT tenant_id,
       date_trunc('month', scheduled_start)::date AS month_start,
       status,
       COUNT(*) AS job_count
FROM jobs
WHERE archived_at IS NULL
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX mv_jobs_monthly_pk ON mv_jobs_monthly(tenant_id, month_start, status);

-- ---------------------------------------------------------------------------
-- mv_time_to_claim: avg seconds from job creation to first ASSIGNED_TO_TECHNICIAN event
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_time_to_claim AS
SELECT j.tenant_id,
       date_trunc('day', j.created_at)::date AS day,
       AVG(EXTRACT(EPOCH FROM (h.created_at - j.created_at))) AS avg_seconds
FROM jobs j
JOIN job_status_history h ON h.job_id = j.id AND h.to_status = 'ASSIGNED_TO_TECHNICIAN'
WHERE j.archived_at IS NULL
GROUP BY 1, 2;

CREATE UNIQUE INDEX mv_time_to_claim_pk ON mv_time_to_claim(tenant_id, day);

-- ---------------------------------------------------------------------------
-- mv_time_to_completion: avg seconds from job creation to COMPLETED event
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_time_to_completion AS
SELECT j.tenant_id,
       date_trunc('day', j.created_at)::date AS day,
       AVG(EXTRACT(EPOCH FROM (h.created_at - j.created_at))) AS avg_seconds
FROM jobs j
JOIN job_status_history h ON h.job_id = j.id AND h.to_status = 'COMPLETED'
WHERE j.archived_at IS NULL
GROUP BY 1, 2;

CREATE UNIQUE INDEX mv_time_to_completion_pk ON mv_time_to_completion(tenant_id, day);

-- ---------------------------------------------------------------------------
-- mv_tech_completion_rate: per-technician assignment and completion totals
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_tech_completion_rate AS
SELECT tenant_id,
       assigned_technician_id,
       COUNT(*) FILTER (WHERE status != 'CANCELLED') AS total_assigned,
       COUNT(*) FILTER (WHERE status = 'COMPLETED')  AS total_completed
FROM jobs
WHERE archived_at IS NULL
  AND assigned_technician_id IS NOT NULL
GROUP BY 1, 2;

CREATE UNIQUE INDEX mv_tech_completion_rate_pk
    ON mv_tech_completion_rate(tenant_id, assigned_technician_id);
