package com.teamhub.job;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobStatusChanged;
import com.teamhub.exception.DomainException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the PRD §9.2 job state machine for every status transition.
 *
 * <p>The transition table is a data-driven {@code Map} keyed by a {@code (fromStatus, toStatus)}
 * pair whose value is the set of actor roles permitted to perform that transition. Unknown pairs or
 * roles that are not in the allowed set cause an {@link IllegalStateTransitionException} (HTTP
 * 409).
 *
 * <p>On a successful transition the method atomically:
 *
 * <ol>
 *   <li>Updates {@link Job#getStatus()} (triggers {@code @Version} optimistic lock).
 *   <li>Inserts a {@link JobStatusHistory} row.
 *   <li>Publishes a {@code JobStatusChanged} event via the transactional outbox.
 * </ol>
 */
@Service
public class JobStateMachineService {

  /**
   * Data-driven transition table (PRD §9.2).
   *
   * <p>Key: {@code (fromStatus, toStatus)} pair; value: set of roles allowed to perform the
   * transition. Role names must match the JWT {@code role} claim values ({@code ADMIN}, {@code
   * DISPATCHER}, {@code TECHNICIAN}).
   *
   * <p>Notes:
   *
   * <ul>
   *   <li>{@code Any → CANCELED} is listed explicitly for every non-terminal status so the lookup
   *       stays O(1) and avoids wildcard matching logic.
   *   <li>{@code COMPLETED → *} admits Admin only — covers re-open and override scenarios (PRD
   *       §9.2: "Completed → (any) | Admin only").
   * </ul>
   */
  static final Map<Map.Entry<JobStatus, JobStatus>, Set<String>> TRANSITIONS;

  static {
    Map<Map.Entry<JobStatus, JobStatus>, Set<String>> m = new HashMap<>();

    // Submitted → Assigned to Technician (PRD §9.2)
    m.put(
        pair(JobStatus.SUBMITTED, JobStatus.ASSIGNED_TO_TECHNICIAN),
        Set.of("TECHNICIAN", "DISPATCHER", "ADMIN"));

    // Assigned to Technician → In Progress (PRD §9.2)
    m.put(pair(JobStatus.ASSIGNED_TO_TECHNICIAN, JobStatus.IN_PROGRESS), Set.of("TECHNICIAN"));

    // In Progress → Pending (PRD §9.2)
    m.put(pair(JobStatus.IN_PROGRESS, JobStatus.PENDING), Set.of("TECHNICIAN"));

    // In Progress → Done Pending Approval (PRD §9.2)
    m.put(pair(JobStatus.IN_PROGRESS, JobStatus.DONE_PENDING_APPROVAL), Set.of("TECHNICIAN"));

    // In Progress → Completed (PRD §9.2: Technician only if Close Jobs is enabled)
    // TO-DO (TASK-053): check tenant.close_jobs_enabled toggle before allowing this transition
    m.put(pair(JobStatus.IN_PROGRESS, JobStatus.COMPLETED), Set.of("TECHNICIAN"));

    // Pending → In Progress (PRD §9.2)
    m.put(pair(JobStatus.PENDING, JobStatus.IN_PROGRESS), Set.of("TECHNICIAN"));

    // Done Pending Approval → Completed (PRD §9.2: Dispatcher / Admin only)
    m.put(
        pair(JobStatus.DONE_PENDING_APPROVAL, JobStatus.COMPLETED), Set.of("DISPATCHER", "ADMIN"));

    // Any → Canceled (PRD §9.2: Dispatcher / Admin only)
    for (JobStatus from :
        new JobStatus[] {
          JobStatus.SUBMITTED,
          JobStatus.ASSIGNED_TO_TECHNICIAN,
          JobStatus.IN_PROGRESS,
          JobStatus.PENDING,
          JobStatus.DONE_PENDING_APPROVAL
        }) {
      m.put(pair(from, JobStatus.CANCELED), Set.of("DISPATCHER", "ADMIN"));
    }

    // Canceled → Submitted (PRD §9.2: Dispatcher / Admin only)
    m.put(pair(JobStatus.CANCELED, JobStatus.SUBMITTED), Set.of("DISPATCHER", "ADMIN"));

    // Completed → any (PRD §9.2: Admin only — re-open / override)
    for (JobStatus to :
        new JobStatus[] {
          JobStatus.SUBMITTED,
          JobStatus.ASSIGNED_TO_TECHNICIAN,
          JobStatus.IN_PROGRESS,
          JobStatus.PENDING,
          JobStatus.DONE_PENDING_APPROVAL,
          JobStatus.CANCELED
        }) {
      m.put(pair(JobStatus.COMPLETED, to), Set.of("ADMIN"));
    }

    TRANSITIONS = Collections.unmodifiableMap(m);
  }

  private final JobRepository jobRepository;

  private final JobStatusHistoryRepository historyRepository;

  private final OutboxPublisher outboxPublisher;

  public JobStateMachineService(
      JobRepository jobRepository,
      JobStatusHistoryRepository historyRepository,
      OutboxPublisher outboxPublisher) {
    this.jobRepository = jobRepository;
    this.historyRepository = historyRepository;
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Validates and executes a job status transition atomically.
   *
   * <p>All writes (job update, history row, outbox event) share the same database transaction. The
   * {@link OutboxPublisher} uses {@code Propagation.MANDATORY} — it requires an active transaction.
   *
   * @param jobId the job to transition
   * @param toStatus the target status
   * @param actorUserId UUID of the user performing the transition
   * @param actorRole role claim of the actor ({@code "TECHNICIAN"}, {@code "DISPATCHER"}, or {@code
   *     "ADMIN"})
   * @param note optional free-text note stored in the history row
   * @throws DomainException HTTP 404 if the job does not exist
   * @throws IllegalStateTransitionException HTTP 409 if the transition or role is not allowed
   */
  @Transactional
  public void transition(
      UUID jobId, JobStatus toStatus, UUID actorUserId, String actorRole, String note) {

    Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));

    JobStatus fromStatus = job.getStatus();

    Set<String> allowedRoles = TRANSITIONS.get(pair(fromStatus, toStatus));
    if (allowedRoles == null || !allowedRoles.contains(actorRole)) {
      throw new IllegalStateTransitionException(fromStatus, toStatus);
    }

    job.setStatus(toStatus);
    jobRepository.save(job);

    historyRepository.save(
        JobStatusHistory.builder()
            .tenantId(job.getTenantId())
            .jobId(jobId)
            .fromStatus(fromStatus.name())
            .toStatus(toStatus.name())
            .actorUserId(actorUserId)
            .actorRole(actorRole)
            .transitionNote(note)
            .build());

    outboxPublisher.publish(
        "JobStatusChanged",
        "Job",
        jobId,
        new JobStatusChanged(
            UUID.randomUUID(),
            job.getTenantId(),
            jobId,
            fromStatus.name(),
            toStatus.name(),
            actorUserId));
  }

  private static Map.Entry<JobStatus, JobStatus> pair(JobStatus from, JobStatus to) {
    return new AbstractMap.SimpleImmutableEntry<>(from, to);
  }
}
