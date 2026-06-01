package com.teamhub.job;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.job.dto.CreateJobRequest;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.job.dto.UpdateJobRequest;
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
 */
@Service
@Transactional
public class JobService {

  private static final int SRID_WGS84 = 4326;

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private static final String ROLE_TECHNICIAN = "TECHNICIAN";

  private final JobRepository jobRepository;

  private final JobCustomFieldValidator customFieldValidator;

  private final OutboxPublisher outboxPublisher;

  public JobService(
      JobRepository jobRepository,
      JobCustomFieldValidator customFieldValidator,
      OutboxPublisher outboxPublisher) {
    this.jobRepository = jobRepository;
    this.customFieldValidator = customFieldValidator;
    this.outboxPublisher = outboxPublisher;
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
   * Retrieves a single active job by ID, scoped to the given tenant.
   *
   * @throws JobNotFoundException when the job does not exist or belongs to another tenant
   */
  @Transactional(readOnly = true)
  public JobResponse getById(UUID tenantId, UUID jobId) {
    Job job =
        jobRepository
            .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
    return JobResponse.from(job);
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

  /**
   * Exposes {@link TenantContext#get()} for use by the controller in a testable way; kept
   * package-private for consistency with the customer pattern.
   */
  static UUID currentTenant() {
    return TenantContext.get();
  }
}
