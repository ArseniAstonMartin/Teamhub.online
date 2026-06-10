package com.teamhub.job;

import com.teamhub.job.dto.JobStatusHistoryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to job status transition history (PRD §9; TASK-124).
 *
 * <p>Tenant isolation is enforced in the service layer by validating the parent job belongs to the
 * caller's tenant before querying {@code job_status_history}. Rows are filtered by {@code
 * tenant_id} even though RLS is also enabled on the table.
 */
@Service
@Transactional(readOnly = true)
public class JobStatusHistoryService {

  private final JobRepository jobRepository;

  private final JobStatusHistoryRepository historyRepository;

  public JobStatusHistoryService(
      JobRepository jobRepository, JobStatusHistoryRepository historyRepository) {
    this.jobRepository = jobRepository;
    this.historyRepository = historyRepository;
  }

  /**
   * Returns all status transitions for a job in oldest-first order.
   *
   * @param tenantId the requesting tenant
   * @param jobId the job whose history to list
   * @return history rows ordered by {@code created_at ASC}
   * @throws JobNotFoundException if the job does not exist or belongs to another tenant
   */
  public List<JobStatusHistoryResponse> listStatusHistory(UUID tenantId, UUID jobId) {
    jobRepository
        .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
        .orElseThrow(() -> new JobNotFoundException(jobId));

    return historyRepository.findByJobIdAndTenantIdOrderByCreatedAtAsc(jobId, tenantId).stream()
        .map(JobStatusHistoryResponse::from)
        .toList();
  }
}
