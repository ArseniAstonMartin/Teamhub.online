package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.metrics.JobMetrics;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link JobService} cancel/reopen flows (TASK-127). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class JobServiceUnitTest {

  @Mock private JobRepository jobRepository;

  @Mock private CustomerRepository customerRepository;

  @Mock private JobCustomFieldValidator customFieldValidator;

  @Mock private OutboxPublisher outboxPublisher;

  @Mock private JobMetrics jobMetrics;

  @Mock private JobStateMachineService jobStateMachineService;

  @Mock private QuotaEnforcementService quotaEnforcementService;

  private JobService service() {
    return new JobService(
        jobRepository,
        customerRepository,
        customFieldValidator,
        outboxPublisher,
        jobMetrics,
        jobStateMachineService,
        quotaEnforcementService);
  }

  @Test
  void cancelJob_setsReasonAndTransitionsToCanceled() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    JobResponse response =
        service()
            .cancelJob(tenantId, jobId, CancellationReason.TOO_EXPENSIVE, actorId, "DISPATCHER");

    assertThat(response.cancellationReason()).isEqualTo(CancellationReason.TOO_EXPENSIVE);
    verify(jobStateMachineService)
        .transition(jobId, JobStatus.CANCELED, actorId, "DISPATCHER", null);
  }

  @Test
  void reopenJob_clearsSubstatusFieldsAndTransitionsToSubmitted() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setCancellationReason(CancellationReason.OTHER);
    job.setPendingReason(PendingReason.WAITING_FOR_PARTS);
    job.setInProgressSubstatus(InProgressSubstatus.ON_THE_WAY);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    JobResponse response = service().reopenJob(tenantId, jobId, actorId, "ADMIN");

    assertThat(response.cancellationReason()).isNull();
    assertThat(job.getPendingReason()).isNull();
    assertThat(job.getInProgressSubstatus()).isNull();
    verify(jobStateMachineService).transition(jobId, JobStatus.SUBMITTED, actorId, "ADMIN", null);
  }

  private static Job activeJob(UUID tenantId, UUID jobId) {
    Job job = new Job();
    job.setId(jobId);
    job.setTenantId(tenantId);
    job.setCustomerId(UUID.randomUUID());
    job.setStatus(JobStatus.ASSIGNED_TO_TECHNICIAN);
    return job;
  }
}
