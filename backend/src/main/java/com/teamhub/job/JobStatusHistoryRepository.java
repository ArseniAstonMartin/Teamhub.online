package com.teamhub.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read/write access to the {@code job_status_history} table (TASK-043). */
public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, UUID> {

  /** Returns all history rows for a given job ordered by creation time ascending. */
  List<JobStatusHistory> findByJobIdOrderByCreatedAtAsc(UUID jobId);

  /** Returns tenant-scoped history rows for a job ordered by creation time ascending (TASK-124). */
  List<JobStatusHistory> findByJobIdAndTenantIdOrderByCreatedAtAsc(UUID jobId, UUID tenantId);
}
