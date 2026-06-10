package com.teamhub.job;

import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobClaimed;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.job.dto.CreateJobRequest;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.job.dto.UpdateJobRequest;
import com.teamhub.metrics.JobMetrics;
import com.teamhub.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for job CRUD operations (PRD §9, §10, §22, TASK-044).
 *
 * <p>Role-scoped list: TECHNICIAN sees only jobs assigned to them or in {@code SUBMITTED} (shared
 * pool); DISPATCHER and ADMIN see all tenant jobs (PRD §10.1). Status changes are handled
 * separately by {@link JobStateMachineService}; this service only modifies non-status fields.
 *
 * <p>The claim operation (PRD §10.2, TASK-045) uses a single atomic native UPDATE with optimistic
 * locking to ensure "first technician wins" semantics without SELECT-FOR-UPDATE.
 */
@Service
@Transactional
public class JobService {

  private static final int SRID_WGS84 = 4326;

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private static final String ROLE_TECHNICIAN = "TECHNICIAN";

  private final JobRepository jobRepository;

  private final CustomerRepository customerRepository;

  private final JobCustomFieldValidator customFieldValidator;

  private final OutboxPublisher outboxPublisher;

  private final JobMetrics jobMetrics;

  private final JobStateMachineService jobStateMachineService;

  private final QuotaEnforcementService quotaEnforcementService;

  public JobService(
      JobRepository jobRepository,
      CustomerRepository customerRepository,
      JobCustomFieldValidator customFieldValidator,
      OutboxPublisher outboxPublisher,
      JobMetrics jobMetrics,
      JobStateMachineService jobStateMachineService,
      QuotaEnforcementService quotaEnforcementService) {
    this.jobRepository = jobRepository;
    this.customerRepository = customerRepository;
    this.customFieldValidator = customFieldValidator;
    this.outboxPublisher = outboxPublisher;
    this.jobMetrics = jobMetrics;
    this.jobStateMachineService = jobStateMachineService;
    this.quotaEnforcementService = quotaEnforcementService;
  }

  /**
   * Returns a paginated, role-scoped list of active jobs for the given tenant.
   *
   * <p>TECHNICIAN callers receive only jobs where {@code assignedTechnicianId = actorUserId OR
   * status = SUBMITTED}. All other roles receive the full tenant job list. All filters are
   * optional.
   *
   * @param tenantId the owning tenant
   * @param actorRole JWT {@code role} claim of the caller
   * @param actorUserId UUID of the caller (used for technician scoping)
   * @param statusFilter optional status filter
   * @param assigneeFilter optional assignee filter (ignored in technician scope)
   * @param fromDate optional lower bound on {@code scheduledStart}
   * @param toDate optional upper bound on {@code scheduledStart}
   * @param search optional free-text search
   * @param pageable pagination and sort parameters
   */
  @Transactional(readOnly = true)
  public Page<JobResponse> list(
      UUID tenantId,
      String actorRole,
      UUID actorUserId,
      JobStatus statusFilter,
      UUID assigneeFilter,
      Instant fromDate,
      Instant toDate,
      String search,
      Pageable pageable) {
    boolean technicianScope = ROLE_TECHNICIAN.equals(actorRole);
    Specification<Job> spec =
        buildSpec(
            tenantId, technicianScope, actorUserId, statusFilter, assigneeFilter, fromDate, toDate);
    return jobRepository.findAll(spec, pageable).map(JobResponse::from);
  }

  private static Specification<Job> buildSpec(
      UUID tenantId,
      boolean technicianScope,
      UUID actorUserId,
      JobStatus statusFilter,
      UUID assigneeFilter,
      Instant fromDate,
      Instant toDate) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always scope to tenant and exclude archived
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      predicates.add(cb.isNull(root.get("archivedAt")));

      if (technicianScope) {
        // TECHNICIAN sees own jobs OR shared pool (SUBMITTED)
        predicates.add(
            cb.or(
                cb.equal(root.get("assignedTechnicianId"), actorUserId),
                cb.equal(root.get("status"), JobStatus.SUBMITTED)));
      } else if (assigneeFilter != null) {
        predicates.add(cb.equal(root.get("assignedTechnicianId"), assigneeFilter));
      }

      if (statusFilter != null) {
        predicates.add(cb.equal(root.get("status"), statusFilter));
      }
      if (fromDate != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), fromDate));
      }
      if (toDate != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), toDate));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Creates a new job with status {@code SUBMITTED} and publishes a {@code JobSubmitted} outbox
   * event in the same transaction (PRD §9, §22.5.5, TASK-044).
   *
   * <p>When {@code req.appointmentSchemaId()} is non-null, custom fields are validated against the
   * referenced schema before persisting.
   *
   * @param tenantId the owning tenant
   * @param req validated request body
   * @param actorUserId UUID of the dispatcher/admin creating the job
   * @return the persisted job as a response DTO
   */
  public JobResponse create(UUID tenantId, CreateJobRequest req, UUID actorUserId) {
    quotaEnforcementService.checkJobQuota(tenantId);

    if (req.appointmentSchemaId() != null) {
      customFieldValidator.validate(tenantId, req.appointmentSchemaId(), req.customFields());
    }

    Job job = new Job();
    job.setTenantId(tenantId);
    job.setCustomerId(req.customerId());
    job.setAppointmentSchemaId(req.appointmentSchemaId());
    job.setStatus(JobStatus.SUBMITTED);
    job.setCustomFields(req.customFields());
    if (req.lat() != null && req.lon() != null) {
      job.setLocation(buildPoint(req.lon(), req.lat()));
    }
    if (req.scheduledStart() != null) {
      job.setScheduledStart(req.scheduledStart().toInstant());
    }
    if (req.scheduledEnd() != null) {
      job.setScheduledEnd(req.scheduledEnd().toInstant());
    }

    job = jobRepository.save(job);

    jobMetrics.recordJobCreated(tenantId.toString());

    outboxPublisher.publish(
        "JobSubmitted",
        "Job",
        job.getId(),
        new JobSubmitted(
            UUID.randomUUID(),
            tenantId,
            job.getId(),
            req.customerId(),
            null,
            job.getScheduledStart()));

    return JobResponse.from(job);
  }

  /**
   * Partially updates non-status fields of an existing active job.
   *
   * <p>Only non-null fields in the request overwrite stored values. Status changes must go through
   * {@link JobStateMachineService#transition} — PATCH never modifies the status field.
   *
   * @throws JobNotFoundException when the job does not exist or belongs to another tenant
   */
  public JobResponse update(UUID tenantId, UUID jobId, UpdateJobRequest req) {
    Job job =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

    if (req.customerId() != null) {
      job.setCustomerId(req.customerId());
    }
    if (req.appointmentSchemaId() != null) {
      job.setAppointmentSchemaId(req.appointmentSchemaId());
    }
    if (req.scheduledStart() != null) {
      job.setScheduledStart(req.scheduledStart().toInstant());
    }
    if (req.scheduledEnd() != null) {
      job.setScheduledEnd(req.scheduledEnd().toInstant());
    }
    if (req.lat() != null && req.lon() != null) {
      job.setLocation(buildPoint(req.lon(), req.lat()));
    }
    if (req.customFields() != null) {
      job.setCustomFields(req.customFields());
    }
    return JobResponse.from(jobRepository.save(job));
  }

  /**
   * Retrieves a single active job by ID, scoped to the given tenant and the caller's role.
   *
   * <p>TECHNICIAN callers can only read a job that is assigned to them or is in {@code SUBMITTED}
   * (shared-pool) state. Any other job within the same tenant throws {@link
   * JobNotAssignedToCallerException} (HTTP 403, PRD §5.2 — {@code job.read.own}). All other roles
   * (DISPATCHER, ADMIN) with {@code PERM_job.read.tenant} may read any job in their tenant.
   *
   * <p>The {@code customerPhone} field in the response is masked (set to {@code null}) when the job
   * is still in {@code SUBMITTED} state (PRD §10.1).
   *
   * @param tenantId the owning tenant (from JWT / TenantContext)
   * @param jobId UUID of the job to retrieve
   * @param actorUserId UUID of the authenticated caller (used for TECHNICIAN scoping)
   * @param actorRole JWT {@code role} claim of the caller
   * @throws JobNotFoundException when the job does not exist or belongs to another tenant
   * @throws JobNotAssignedToCallerException when a TECHNICIAN attempts to read a job not assigned
   *     to them and not in SUBMITTED status
   */
  @Transactional(readOnly = true)
  public JobResponse getById(UUID tenantId, UUID jobId, UUID actorUserId, String actorRole) {
    Job job =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

    if (ROLE_TECHNICIAN.equals(actorRole)) {
      boolean isAssignedToCalller =
          actorUserId != null && actorUserId.equals(job.getAssignedTechnicianId());
      boolean isSharedPool = job.getStatus() == JobStatus.SUBMITTED;
      if (!isAssignedToCalller && !isSharedPool) {
        throw new JobNotAssignedToCallerException(jobId);
      }
    }

    String customerPhone = resolvePhone(job.getCustomerId());
    return JobResponse.from(job, customerPhone);
  }

  /**
   * Claims a {@code SUBMITTED} job for a technician using an optimistic-lock native UPDATE (PRD
   * §10.2, TASK-045).
   *
   * <p>The method loads the job first to validate its current state, then fires the atomic UPDATE.
   * If the UPDATE touches 0 rows — either because another transaction already changed the status,
   * or because the version column was incremented by a concurrent claim — it throws {@link
   * JobAlreadyClaimedException} (HTTP 409, code {@code JOB_ALREADY_CLAIMED}).
   *
   * <p>On success the {@link JobClaimed} event is published via the outbox in the same transaction
   * (propagation MANDATORY).
   *
   * @param tenantId the owning tenant
   * @param jobId UUID of the job to claim
   * @param technicianId UUID of the claiming technician
   * @return the updated job with {@code customerPhone} populated
   * @throws JobAlreadyClaimedException if the job is not found, not SUBMITTED, or the race was lost
   */
  @Transactional
  public JobResponse claim(UUID tenantId, UUID jobId, UUID technicianId) {
    Job job =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobAlreadyClaimedException(jobId));

    if (job.getStatus() != JobStatus.SUBMITTED) {
      throw new JobAlreadyClaimedException(jobId);
    }

    int updated = jobRepository.claimJob(jobId, tenantId, technicianId, job.getVersion());
    if (updated == 0) {
      throw new JobAlreadyClaimedException(jobId);
    }

    // Reload after native UPDATE; @Modifying(clearAutomatically=true) evicted the stale cache entry
    Job claimed =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobAlreadyClaimedException(jobId));

    outboxPublisher.publish(
        "JobClaimed",
        "Job",
        jobId,
        new JobClaimed(UUID.randomUUID(), tenantId, jobId, technicianId, Instant.now()));

    String customerPhone = resolvePhone(claimed.getCustomerId());
    return JobResponse.from(claimed, customerPhone);
  }

  private String resolvePhone(UUID customerId) {
    if (customerId == null) {
      return null;
    }
    return customerRepository.findById(customerId).map(Customer::getPhone).orElse(null);
  }

  /**
   * Soft-archives a job by setting {@code archivedAt} (PRD §9, TASK-044). The job is excluded from
   * default list queries but preserved in history.
   *
   * @throws JobNotFoundException when the job does not exist or belongs to another tenant
   */
  public void archive(UUID tenantId, UUID jobId) {
    Job job =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
    job.setArchivedAt(Instant.now());
    jobRepository.save(job);
  }

  /**
   * Physically deletes a job row from the database (PRD §5.3: Admin only; enforced at controller).
   *
   * @throws JobNotFoundException when the job does not exist or belongs to another tenant
   */
  public void hardDelete(UUID tenantId, UUID jobId) {
    Job job =
        jobRepository
            .findById(jobId)
            .filter(j -> j.getTenantId().equals(tenantId))
            .orElseThrow(() -> new JobNotFoundException(jobId));
    jobRepository.delete(job);
  }

  private static Point buildPoint(double lon, double lat) {
    return GF.createPoint(new Coordinate(lon, lat));
  }

  // =========================================================================
  // Technician transition methods (PRD §9.2, §13, TASK-047)
  // =========================================================================

  /**
   * Transitions a job to {@code IN_PROGRESS} with sub-status {@code WORKING} (PRD §13, TASK-047).
   *
   * <p>The calling technician must be assigned to the job; otherwise throws {@link
   * JobNotAssignedToCallerException} (HTTP 403).
   *
   * @param tenantId the owning tenant
   * @param jobId the job to start
   * @param actorUserId UUID of the calling technician
   * @param actorRole JWT {@code role} claim (must be {@code "TECHNICIAN"})
   * @return the updated job as a response DTO
   * @throws JobNotFoundException if the job does not exist
   * @throws JobNotAssignedToCallerException if the technician is not assigned to this job
   * @throws com.teamhub.job.IllegalStateTransitionException if the current status does not allow
   *     this transition
   */
  @Transactional
  public JobResponse startJob(UUID tenantId, UUID jobId, UUID actorUserId, String actorRole) {
    Job job = loadActive(tenantId, jobId);
    requireAssigned(job, actorUserId);
    job.setInProgressSubstatus(InProgressSubstatus.WORKING);
    jobStateMachineService.transition(jobId, JobStatus.IN_PROGRESS, actorUserId, actorRole, null);
    return JobResponse.from(job);
  }

  /**
   * Transitions a job to {@code PENDING} and records the {@link PendingReason} (PRD §9.1, §13,
   * TASK-047).
   *
   * <p>The calling technician must be assigned to the job. The {@code pendingReason} is required
   * (validated at the controller via {@code @NotNull}).
   *
   * @param tenantId the owning tenant
   * @param jobId the job to pause
   * @param pendingReason the reason the job is being paused (required)
   * @param note optional free-text note stored in the history row
   * @param actorUserId UUID of the calling technician
   * @param actorRole JWT {@code role} claim
   * @return the updated job as a response DTO
   * @throws JobNotAssignedToCallerException if the technician is not assigned to this job
   */
  @Transactional
  public JobResponse pendingJob(
      UUID tenantId,
      UUID jobId,
      PendingReason pendingReason,
      String note,
      UUID actorUserId,
      String actorRole) {
    Job job = loadActive(tenantId, jobId);
    requireAssigned(job, actorUserId);
    job.setPendingReason(pendingReason);
    jobStateMachineService.transition(jobId, JobStatus.PENDING, actorUserId, actorRole, note);
    return JobResponse.from(job);
  }

  /**
   * Transitions a job to {@code DONE_PENDING_APPROVAL} (PRD §9.2, §13, TASK-047).
   *
   * <p>The calling technician must be assigned to the job.
   *
   * @param tenantId the owning tenant
   * @param jobId the job to mark as done pending approval
   * @param note optional free-text note stored in the history row
   * @param actorUserId UUID of the calling technician
   * @param actorRole JWT {@code role} claim
   * @return the updated job as a response DTO
   * @throws JobNotAssignedToCallerException if the technician is not assigned to this job
   */
  @Transactional
  public JobResponse donePendingApproval(
      UUID tenantId, UUID jobId, String note, UUID actorUserId, String actorRole) {
    Job job = loadActive(tenantId, jobId);
    requireAssigned(job, actorUserId);
    jobStateMachineService.transition(
        jobId, JobStatus.DONE_PENDING_APPROVAL, actorUserId, actorRole, note);
    return JobResponse.from(job);
  }

  /**
   * Transitions a job to {@code COMPLETED} (PRD §9.3, §13, TASK-047).
   *
   * <p>The calling technician must be assigned to the job. The state machine enforces that the
   * tenant's {@code close_jobs_enabled} toggle is on, throwing {@link CloseJobsDisabledException}
   * (HTTP 403) when it is off.
   *
   * @param tenantId the owning tenant
   * @param jobId the job to complete
   * @param note optional free-text note stored in the history row
   * @param actorUserId UUID of the calling technician
   * @param actorRole JWT {@code role} claim
   * @return the updated job as a response DTO
   * @throws JobNotAssignedToCallerException if the technician is not assigned to this job
   * @throws CloseJobsDisabledException if the tenant has not enabled Close Jobs
   */
  @Transactional
  public JobResponse completeJob(
      UUID tenantId, UUID jobId, String note, UUID actorUserId, String actorRole) {
    Job job = loadActive(tenantId, jobId);
    requireAssigned(job, actorUserId);
    jobStateMachineService.transition(jobId, JobStatus.COMPLETED, actorUserId, actorRole, note);
    return JobResponse.from(job);
  }

  /**
   * Cancels a job from any non-terminal status (PRD §9.2, §9.4; TASK-127).
   *
   * <p>Dispatcher and Admin callers only (enforced at controller via {@code PERM_job.edit}).
   * Records {@code cancellationReason} and transitions to {@code CANCELED}.
   */
  @Transactional
  public JobResponse cancelJob(
      UUID tenantId, UUID jobId, CancellationReason reason, UUID actorUserId, String actorRole) {
    Job job = loadActive(tenantId, jobId);
    job.setCancellationReason(reason);
    jobStateMachineService.transition(jobId, JobStatus.CANCELED, actorUserId, actorRole, null);
    return JobResponse.from(job);
  }

  /**
   * Reopens a canceled job back to {@code SUBMITTED} (PRD §9.2, §9.4; TASK-127).
   *
   * <p>Admin callers only (enforced at controller via {@code PERM_job.hard_delete}). Clears
   * cancellation, pending, and in-progress sub-status fields per PRD §9.
   */
  @Transactional
  public JobResponse reopenJob(UUID tenantId, UUID jobId, UUID actorUserId, String actorRole) {
    Job job = loadActive(tenantId, jobId);
    job.setCancellationReason(null);
    job.setPendingReason(null);
    job.setInProgressSubstatus(null);
    jobStateMachineService.transition(jobId, JobStatus.SUBMITTED, actorUserId, actorRole, null);
    return JobResponse.from(job);
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  private Job loadActive(UUID tenantId, UUID jobId) {
    return jobRepository
        .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
        .orElseThrow(() -> new JobNotFoundException(jobId));
  }

  private void requireAssigned(Job job, UUID actorUserId) {
    if (!actorUserId.equals(job.getAssignedTechnicianId())) {
      throw new JobNotAssignedToCallerException(job.getId());
    }
  }

  /**
   * Exposes {@link TenantContext#get()} for use by the controller in a testable way; kept
   * package-private for consistency with the customer pattern.
   */
  static UUID currentTenant() {
    return TenantContext.get();
  }
}
