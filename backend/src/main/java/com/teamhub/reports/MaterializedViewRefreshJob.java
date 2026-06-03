package com.teamhub.reports;

import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that refreshes the six report materialized views every 15 minutes (TASK-068, PRD
 * §18.2).
 *
 * <p>Each view is refreshed with {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} so that concurrent
 * read queries are never blocked. This requires a unique index on every view — created in migration
 * V28.
 *
 * <p>{@code @SchedulerLock} guarantees single-leader execution across all JVM nodes. {@code
 * lockAtMostFor = "PT10M"} releases the lock automatically if the node crashes; {@code
 * lockAtLeastFor = "PT1M"} prevents spurious re-runs in fast test cycles.
 *
 * <p>Each view refresh is attempted independently. A failure for one view is logged but does not
 * prevent refreshing the remaining views.
 */
@Component
public class MaterializedViewRefreshJob {

  private static final Logger log = LoggerFactory.getLogger(MaterializedViewRefreshJob.class);

  /** Ordered list of all six report materialized views. */
  static final List<String> VIEWS =
      List.of(
          "mv_jobs_daily",
          "mv_jobs_weekly",
          "mv_jobs_monthly",
          "mv_time_to_claim",
          "mv_time_to_completion",
          "mv_tech_completion_rate");

  private final JdbcTemplate jdbc;

  public MaterializedViewRefreshJob(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Spring-scheduler entry point — runs every 15 minutes.
   *
   * <p>{@code @SchedulerLock} ensures at most one node executes this at a time.
   */
  @Scheduled(fixedDelay = 900_000)
  @SchedulerLock(
      name = "materialized-view-refresh",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT1M")
  public void schedule() {
    refreshAll();
  }

  /**
   * Refreshes all six materialized views concurrently. Exposed as a public method so that
   * integration tests can trigger a refresh without waiting for the scheduler.
   */
  public void refreshAll() {
    log.info("MaterializedViewRefreshJob: starting refresh of {} views", VIEWS.size());
    long jobStart = System.currentTimeMillis();

    for (String view : VIEWS) {
      long start = System.currentTimeMillis();
      try {
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + view);
        long elapsed = System.currentTimeMillis() - start;
        log.info("MaterializedViewRefreshJob: refreshed {} in {}ms", view, elapsed);
      } catch (Exception ex) {
        long elapsed = System.currentTimeMillis() - start;
        log.error("MaterializedViewRefreshJob: failed to refresh {} after {}ms", view, elapsed, ex);
      }
    }

    long total = System.currentTimeMillis() - jobStart;
    log.info("MaterializedViewRefreshJob: finished in {}ms", total);
  }
}
