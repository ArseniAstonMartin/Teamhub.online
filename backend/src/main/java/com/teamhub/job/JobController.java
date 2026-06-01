package com.teamhub.job;

import com.teamhub.job.dto.CreateJobRequest;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.job.dto.UpdateJobRequest;
import com.teamhub.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job CRUD endpoints (PRD §9, §10, §22, TASK-044).
 *
 * <p>Permission model (PRD §5.2, §5.3):
 *
 * <ul>
 *   <li>GET /jobs — requires {@code PERM_job.read.own} (TECHNICIAN) or {@code PERM_job.read.tenant}
 *       (DISPATCHER/ADMIN). Both authorities are checked with OR semantics via a single expression.
 *       The service layer applies role-scoped filtering for TECHNICIAN callers.
 *   <li>POST /jobs — requires {@code PERM_job.create} (DISPATCHER + ADMIN).
 *   <li>GET /jobs/{id} — same read-permission guard as the list endpoint; the service applies
 *       role-scoped lookup.
 *   <li>PATCH /jobs/{id} — requires {@code PERM_job.edit} (DISPATCHER + ADMIN).
 *   <li>DELETE /jobs/{id} — requires {@code PERM_job.delete} (DISPATCHER + ADMIN, soft-archive).
 *   <li>DELETE /jobs/{id}/hard-delete — requires {@code PERM_job.hard_delete} (ADMIN only).
 * </ul>
 *
 * <p>{@code tenantId} is read from {@link TenantContext} (set by {@code TenantContextFilter} after
 * JWT validation). The {@code userId} is extracted from the JWT {@code sub} claim.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job CRUD endpoints — PRD §9, §10, §22")
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  /**
   * Lists active jobs with optional filters. TECHNICIAN callers see only jobs assigned to them or
   * in {@code SUBMITTED} (shared pool); DISPATCHER/ADMIN see all tenant jobs (PRD §10.1).
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_job.read.own') or hasAuthority('PERM_job.read.tenant')")
  @Operation(
      summary = "List jobs",
      description =
          "Paginated list with optional filters. TECHNICIAN receives only own + shared-pool jobs.")
  public Page<JobResponse> list(
      @RequestParam(required = false) JobStatus status,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20) Pageable pageable,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    Instant fromInstant = from != null ? from.toInstant() : null;
    Instant toInstant = to != null ? to.toInstant() : null;
    return jobService.list(
        tenantId,
        actorRole,
        actorUserId,
        status,
        assigneeId,
        fromInstant,
        toInstant,
        search,
        pageable);
  }

  /** Creates a new job with status {@code SUBMITTED} and publishes a {@code JobSubmitted} event. */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_job.create')")
  @Operation(summary = "Create a job", description = "Creates a job with status SUBMITTED.")
  public ResponseEntity<JobResponse> create(
      @Valid @RequestBody CreateJobRequest request, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    JobResponse response = jobService.create(tenantId, request, actorUserId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Returns a single active job by ID, scoped to the caller's tenant and role. */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_job.read.own') or hasAuthority('PERM_job.read.tenant')")
  @Operation(summary = "Get a job by ID")
  public JobResponse get(@PathVariable UUID id) {
    UUID tenantId = TenantContext.get();
    return jobService.getById(tenantId, id);
  }

  /** Partially updates non-status fields of an existing job. */
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_job.edit')")
  @Operation(
      summary = "Partially update a job",
      description = "Only non-null fields are written. Status changes go through /transition.")
  public JobResponse update(@PathVariable UUID id, @RequestBody UpdateJobRequest request) {
    UUID tenantId = TenantContext.get();
    return jobService.update(tenantId, id, request);
  }

  /**
   * Soft-archives a job (sets {@code archivedAt}). The job is excluded from default list queries.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_job.delete')")
  @Operation(
      summary = "Soft-archive a job",
      description = "Sets archived_at; excluded from default list.")
  public ResponseEntity<Void> archive(@PathVariable UUID id) {
    UUID tenantId = TenantContext.get();
    jobService.archive(tenantId, id);
    return ResponseEntity.noContent().build();
  }

  /** Physically deletes a job row. Admin only (enforced by {@code PERM_job.hard_delete}). */
  @DeleteMapping("/{id}/hard-delete")
  @PreAuthorize("hasAuthority('PERM_job.hard_delete')")
  @Operation(
      summary = "Hard-delete a job",
      description = "Admin only. Permanently removes the row.")
  public ResponseEntity<Void> hardDelete(@PathVariable UUID id) {
    UUID tenantId = TenantContext.get();
    jobService.hardDelete(tenantId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Atomically claims a {@code SUBMITTED} job for the calling technician (PRD §10.2, TASK-045).
   *
   * <p>Uses optimistic locking: the service fires a single native UPDATE with a version check.
   * Exactly one concurrent caller wins; the others receive 409 {@code JOB_ALREADY_CLAIMED}.
   *
   * <p>The response includes {@code customerPhone} because the job is no longer in shared-pool
   * state after a successful claim (PRD §10.1).
   */
  @PostMapping("/{id}/claim")
  @PreAuthorize("hasAuthority('PERM_job.read.own')")
  @Operation(
      summary = "Claim a job",
      description =
          "Technician claims a SUBMITTED job from the shared pool. Returns 409 if the job was"
              + " already claimed (PRD §10.2).")
  public JobResponse claim(@PathVariable UUID id, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    return jobService.claim(tenantId, id, actorUserId);
  }

  /** Extracts the {@code sub} claim as a UUID from the validated JWT. */
  private static UUID extractUserId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
      return UUID.fromString(jwtToken.getToken().getSubject());
    }
    return null;
  }

  /** Extracts the {@code role} claim string from the validated JWT. */
  private static String extractRole(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
      return jwtToken.getToken().getClaimAsString("role");
    }
    return null;
  }
}
