package com.teamhub.job;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link Job} (PRD §9, §22, TASK-042, TASK-044). */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

  /**
   * Returns an active (non-archived) job for the given tenant and id.
   *
   * <p>Used by update/archive/hard-delete paths to ensure cross-tenant isolation even when the test
   * DataSource connects as the postgres super-user (bypasses RLS).
   */
  Optional<Job> findByIdAndTenantIdAndArchivedAtIsNull(UUID id, UUID tenantId);

  /**
   * Counts active (non-archived) jobs created at or after {@code periodStart} for the tenant
   * (TASK-064, PRD §17.3).
   *
   * <p>Used by {@code QuotaEnforcementService} to enforce the monthly job quota before a new job is
   * created.
   */
  long countByTenantIdAndCreatedAtGreaterThanEqualAndArchivedAtIsNull(
      UUID tenantId, Instant periodStart);

  /**
   * Atomically claims a {@code SUBMITTED} job for a technician using an optimistic-lock UPDATE (PRD
   * §10.2).
   *
   * <p>The WHERE clause includes both {@code status = 'SUBMITTED'} and {@code version =
   * :currentVersion}, which means only one concurrent caller can win the race: the first UPDATE to
   * commit changes the version, causing every subsequent attempt with the same {@code
   * currentVersion} to match 0 rows.
   *
   * @return 1 if the job was claimed, 0 if the race was lost or the job is no longer SUBMITTED
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE jobs"
              + " SET status = 'ASSIGNED_TO_TECHNICIAN',"
              + "     assigned_technician_id = :technicianId,"
              + "     version = version + 1"
              + " WHERE id = :jobId"
              + "   AND tenant_id = :tenantId"
              + "   AND status = 'SUBMITTED'"
              + "   AND version = :currentVersion",
      nativeQuery = true)
  int claimJob(
      @Param("jobId") UUID jobId,
      @Param("tenantId") UUID tenantId,
      @Param("technicianId") UUID technicianId,
      @Param("currentVersion") int currentVersion);
}
