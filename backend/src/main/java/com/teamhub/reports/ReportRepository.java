package com.teamhub.reports;

import com.teamhub.reports.dto.AvgTimesResponse;
import com.teamhub.reports.dto.DayBucket;
import com.teamhub.reports.dto.TechnicianRateEntry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Native-SQL report queries against the {@code jobs} and {@code job_status_history} tables (PRD
 * §18.1, §18.2, TASK-067).
 *
 * <p>All queries scope to a single tenant and honour the {@code archived_at IS NULL} soft-delete
 * filter. Materialized views are introduced in TASK-068; for now these queries run directly against
 * the live tables.
 */
@Repository
public class ReportRepository {

  private final JdbcTemplate jdbc;

  public ReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Returns job counts grouped by day and status for the given tenant and date range.
   *
   * <p>The result rows are pivoted in memory into {@link DayBucket} objects with three counters:
   * created (all statuses), completed, and cancelled.
   *
   * @param tenantId the owning tenant
   * @param from inclusive start date (UTC)
   * @param to inclusive end date (UTC)
   */
  public List<DayBucket> getJobCountsByDay(UUID tenantId, LocalDate from, LocalDate to) {
    return getJobCountsByPeriod(tenantId, "day", from, to);
  }

  /**
   * Returns job counts grouped by the given period truncation and status.
   *
   * @param tenantId the owning tenant
   * @param period one of {@code day}, {@code week}, {@code month}, {@code year}
   * @param from inclusive start date (UTC)
   * @param to inclusive end date (UTC)
   */
  public List<DayBucket> getJobCountsByPeriod(
      UUID tenantId, String period, LocalDate from, LocalDate to) {

    // Validate the period value to prevent SQL injection (not a bind parameter in date_trunc).
    if (!List.of("day", "week", "month", "year").contains(period)) {
      throw new IllegalArgumentException("Invalid period: " + period);
    }

    String sql =
        """
        SELECT date_trunc('%s', scheduled_start AT TIME ZONE 'UTC') AS bucket,
               status,
               COUNT(*) AS cnt
        FROM jobs
        WHERE tenant_id = ?::uuid
          AND scheduled_start >= ?::timestamptz
          AND scheduled_start < (?::date + INTERVAL '1 day')::timestamptz
          AND archived_at IS NULL
        GROUP BY 1, 2
        ORDER BY 1
        """
            .formatted(period);

    // Collect raw rows keyed by bucket date.
    var rawRows = jdbc.queryForList(sql, tenantId.toString(), from.toString(), to.toString());

    // Pivot: aggregate per bucket-date into DayBucket counters.
    var bucketMap = new java.util.LinkedHashMap<LocalDate, long[]>();
    for (var row : rawRows) {
      Object bucketObj = row.get("bucket");
      LocalDate bucketDate;
      if (bucketObj instanceof java.sql.Timestamp ts) {
        bucketDate = ts.toLocalDateTime().toLocalDate();
      } else if (bucketObj instanceof java.time.OffsetDateTime odt) {
        bucketDate = odt.toLocalDate();
      } else {
        bucketDate = LocalDate.parse(bucketObj.toString().substring(0, 10));
      }

      String status = (String) row.get("status");
      long cnt = ((Number) row.get("cnt")).longValue();

      long[] counters = bucketMap.computeIfAbsent(bucketDate, d -> new long[3]);
      // counters[0] = created (all statuses), counters[1] = completed, counters[2] = cancelled
      counters[0] += cnt;
      if ("COMPLETED".equalsIgnoreCase(status)) {
        counters[1] += cnt;
      }
      if ("CANCELED".equalsIgnoreCase(status)) {
        counters[2] += cnt;
      }
    }

    List<DayBucket> result = new ArrayList<>(bucketMap.size());
    bucketMap.forEach(
        (date, counters) -> result.add(new DayBucket(date, counters[0], counters[1], counters[2])));
    return result;
  }

  /**
   * Returns the average number of seconds between job creation and the first {@code
   * ASSIGNED_TO_TECHNICIAN} transition (time-to-claim), and between job creation and the {@code
   * COMPLETED} transition (time-to-completion).
   *
   * @param tenantId the owning tenant
   * @param from inclusive start date (UTC)
   * @param to inclusive end date (UTC)
   */
  public AvgTimesResponse getAvgTimes(UUID tenantId, LocalDate from, LocalDate to) {
    String claimSql =
        """
        SELECT AVG(EXTRACT(EPOCH FROM (h.created_at - j.created_at))) AS avg_claim_secs
        FROM jobs j
        JOIN (
            SELECT job_id, MIN(created_at) AS created_at
            FROM job_status_history
            WHERE to_status = 'ASSIGNED_TO_TECHNICIAN'
            GROUP BY job_id
        ) h ON h.job_id = j.id
        WHERE j.tenant_id = ?::uuid
          AND j.created_at >= ?::timestamptz
          AND j.created_at < (?::date + INTERVAL '1 day')::timestamptz
          AND j.archived_at IS NULL
        """;

    String completionSql =
        """
        SELECT AVG(EXTRACT(EPOCH FROM (h.created_at - j.created_at))) AS avg_completion_secs
        FROM jobs j
        JOIN (
            SELECT job_id, MIN(created_at) AS created_at
            FROM job_status_history
            WHERE to_status = 'COMPLETED'
            GROUP BY job_id
        ) h ON h.job_id = j.id
        WHERE j.tenant_id = ?::uuid
          AND j.created_at >= ?::timestamptz
          AND j.created_at < (?::date + INTERVAL '1 day')::timestamptz
          AND j.archived_at IS NULL
        """;

    Double avgClaim =
        jdbc.queryForObject(
            claimSql, Double.class, tenantId.toString(), from.toString(), to.toString());

    Double avgCompletion =
        jdbc.queryForObject(
            completionSql, Double.class, tenantId.toString(), from.toString(), to.toString());

    return new AvgTimesResponse(avgClaim, avgCompletion);
  }

  /**
   * Returns per-technician job assignment and completion counts.
   *
   * @param tenantId the owning tenant
   * @param from inclusive start date (UTC)
   * @param to inclusive end date (UTC)
   */
  public List<TechnicianRateEntry> getTechnicianCompletionRates(
      UUID tenantId, LocalDate from, LocalDate to) {

    String sql =
        """
        SELECT assigned_technician_id,
               COUNT(*) AS total_assigned,
               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS total_completed
        FROM jobs
        WHERE tenant_id = ?::uuid
          AND assigned_technician_id IS NOT NULL
          AND created_at >= ?::timestamptz
          AND created_at < (?::date + INTERVAL '1 day')::timestamptz
          AND archived_at IS NULL
        GROUP BY assigned_technician_id
        ORDER BY total_assigned DESC
        """;

    return jdbc.query(
        sql,
        (rs, rowNum) -> {
          UUID techId = UUID.fromString(rs.getString("assigned_technician_id"));
          long assigned = rs.getLong("total_assigned");
          long completed = rs.getLong("total_completed");
          double rate = assigned > 0 ? (double) completed / assigned : 0.0;
          return new TechnicianRateEntry(techId, assigned, completed, rate);
        },
        tenantId.toString(),
        from.toString(),
        to.toString());
  }
}
