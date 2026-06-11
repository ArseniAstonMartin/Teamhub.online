package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.auth.UserNameProjection;
import com.teamhub.auth.UserRepository;
import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.metrics.JobMetrics;
import com.teamhub.shared.IdNameProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Unit tests for {@link JobService} cancel/reopen and name enrichment (TASK-127/130). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class JobServiceUnitTest {

  @Mock private JobRepository jobRepository;

  @Mock private CustomerRepository customerRepository;

  @Mock private UserRepository userRepository;

  @Mock private JobCustomFieldValidator customFieldValidator;

  @Mock private OutboxPublisher outboxPublisher;

  @Mock private JobMetrics jobMetrics;

  @Mock private JobStateMachineService jobStateMachineService;

  @Mock private QuotaEnforcementService quotaEnforcementService;

  private JobService service() {
    return new JobService(
        jobRepository,
        customerRepository,
        userRepository,
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
  void getById_enrichesCustomerAndAssigneeNames() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setCustomerId(customerId);
    job.setAssignedTechnicianId(assigneeId);

    Customer customer = new Customer();
    customer.setPhone("+15035551212");

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerRepository.findIdAndNameByTenantIdAndIdIn(tenantId, List.of(customerId)))
        .thenReturn(List.of(idNameProjection(customerId, "Acme Plumbing")));
    when(userRepository.findIdNameEmailByTenantIdAndIdIn(tenantId, List.of(assigneeId)))
        .thenReturn(List.of(userNameProjection(assigneeId, "Tech One", "tech@acme.com")));

    JobResponse response = service().getById(tenantId, jobId, assigneeId, "ADMIN");

    assertThat(response.customerPhone()).isEqualTo("+15035551212");
    assertThat(response.customerName()).isEqualTo("Acme Plumbing");
    assertThat(response.assigneeName()).isEqualTo("Tech One");
  }

  @Test
  void getById_assigneeWithoutName_fallsBackToEmail() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setCustomerId(customerId);
    job.setAssignedTechnicianId(assigneeId);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
    when(customerRepository.findIdAndNameByTenantIdAndIdIn(tenantId, List.of(customerId)))
        .thenReturn(List.of(idNameProjection(customerId, "Acme Plumbing")));
    when(userRepository.findIdNameEmailByTenantIdAndIdIn(tenantId, List.of(assigneeId)))
        .thenReturn(List.of(userNameProjection(assigneeId, "  ", "tech@acme.com")));

    JobResponse response = service().getById(tenantId, jobId, assigneeId, "ADMIN");

    assertThat(response.customerName()).isEqualTo("Acme Plumbing");
    assertThat(response.assigneeName()).isEqualTo("tech@acme.com");
  }

  @Test
  void getById_withoutAssignee_skipsAssigneeLookup() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setCustomerId(customerId);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
    when(customerRepository.findIdAndNameByTenantIdAndIdIn(tenantId, List.of(customerId)))
        .thenReturn(List.of(idNameProjection(customerId, "Acme Plumbing")));

    JobResponse response = service().getById(tenantId, jobId, UUID.randomUUID(), "ADMIN");

    assertThat(response.customerName()).isEqualTo("Acme Plumbing");
    assertThat(response.assigneeName()).isNull();
  }

  @Test
  void list_emptyPage_returnsResponsesWithoutBatchLookup() {
    UUID tenantId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 20);
    Page<Job> emptyPage = new PageImpl<>(List.of(), pageable, 0);

    when(jobRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

    Page<JobResponse> result =
        service()
            .list(tenantId, "ADMIN", UUID.randomUUID(), null, null, null, null, null, pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void list_withJobs_enrichesCustomerAndAssigneeNames() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setCustomerId(customerId);
    job.setAssignedTechnicianId(assigneeId);

    Pageable pageable = PageRequest.of(0, 20);
    Page<Job> page = new PageImpl<>(List.of(job), pageable, 1);

    when(jobRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(customerRepository.findIdAndNameByTenantIdAndIdIn(eq(tenantId), any()))
        .thenReturn(List.of(idNameProjection(customerId, "Acme Plumbing")));
    when(userRepository.findIdNameEmailByTenantIdAndIdIn(eq(tenantId), any()))
        .thenReturn(List.of(userNameProjection(assigneeId, "Tech One", "tech@acme.com")));

    Page<JobResponse> result =
        service()
            .list(tenantId, "ADMIN", UUID.randomUUID(), null, null, null, null, null, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).customerName()).isEqualTo("Acme Plumbing");
    assertThat(result.getContent().get(0).assigneeName()).isEqualTo("Tech One");
  }

  @Test
  void defineEta_onAssignedJob_transitionsToInProgressOnTheWay() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setAssignedTechnicianId(actorId);
    job.setStatus(JobStatus.ASSIGNED_TO_TECHNICIAN);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    JobResponse response = service().defineEta(tenantId, jobId, 45, actorId, "TECHNICIAN");

    assertThat(response.inProgressSubstatus()).isEqualTo(InProgressSubstatus.ON_THE_WAY);
    assertThat(response.scheduledEnd()).isNotNull();
    assertThat(job.getInProgressSubstatus()).isEqualTo(InProgressSubstatus.ON_THE_WAY);
    verify(jobStateMachineService)
        .transition(jobId, JobStatus.IN_PROGRESS, actorId, "TECHNICIAN", null);
    verify(jobRepository, never()).save(job);
  }

  @Test
  void defineEta_whenJobNotFound_throwsJobNotFoundException() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().defineEta(tenantId, jobId, 45, actorId, "TECHNICIAN"))
        .isInstanceOf(JobNotFoundException.class);
  }

  @Test
  void defineEta_whenNotAssigned_throwsJobNotAssignedToCallerException() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setAssignedTechnicianId(UUID.randomUUID());

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service().defineEta(tenantId, jobId, 45, actorId, "TECHNICIAN"))
        .isInstanceOf(JobNotAssignedToCallerException.class);
  }

  @Test
  void defineEta_whenCompleted_throwsIllegalStateTransitionException() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setAssignedTechnicianId(actorId);
    job.setStatus(JobStatus.COMPLETED);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service().defineEta(tenantId, jobId, 45, actorId, "TECHNICIAN"))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void defineEta_onInProgressJob_updatesFieldsWithoutTransition() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Job job = activeJob(tenantId, jobId);
    job.setAssignedTechnicianId(actorId);
    job.setStatus(JobStatus.IN_PROGRESS);
    job.setInProgressSubstatus(InProgressSubstatus.WORKING);

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));

    JobResponse response = service().defineEta(tenantId, jobId, 30, actorId, "TECHNICIAN");

    assertThat(response.inProgressSubstatus()).isEqualTo(InProgressSubstatus.ON_THE_WAY);
    assertThat(response.scheduledEnd()).isNotNull();
    verify(jobRepository).save(job);
    verify(jobStateMachineService, never()).transition(any(), any(), any(), any(), any());
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

  private static IdNameProjection idNameProjection(UUID id, String name) {
    return new IdNameProjection() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }
    };
  }

  private static UserNameProjection userNameProjection(UUID id, String name, String email) {
    return new UserNameProjection() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getEmail() {
        return email;
      }
    };
  }
}
