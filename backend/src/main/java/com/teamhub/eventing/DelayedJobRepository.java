package com.teamhub.eventing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link DelayedJob} (PRD §22.5.4).
 *
 * <p>The dispatcher uses {@link #findDueJobs(Instant)} to retrieve entries whose {@code run_at} has
 * passed and whose status is still {@code PENDING}. This query is backed by the {@code
 * idx_delayed_jobs_status_run_at} index on {@code (status, run_at)}.
 */
public interface DelayedJobRepository extends JpaRepository<DelayedJob, UUID> {

  /**
   * Returns PENDING jobs whose {@code run_at} is on or before {@code now}, ordered by {@code
   * run_at} ascending so the oldest due job is processed first.
   */
  @Query(
      "SELECT j FROM DelayedJob j WHERE j.status = 'PENDING' AND j.runAt <= :now"
          + " ORDER BY j.runAt ASC")
  List<DelayedJob> findDueJobs(@Param("now") Instant now);

  List<DelayedJob> findByTenantId(UUID tenantId);
}
