package com.teamhub.job;

import com.teamhub.job.dto.CompleteJobRequest;
import com.teamhub.job.dto.CreateJobNoteRequest;
import com.teamhub.job.dto.CreateJobRequest;
import com.teamhub.job.dto.DonePendingApprovalRequest;
import com.teamhub.job.dto.JobNoteResponse;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.job.dto.PendingJobRequest;
import com.teamhub.job.dto.StartJobRequest;
import com.teamhub.job.dto.UpdateJobRequest;
import com.teamhub.shared.TenantContext;
import com.teamhub.storage.MultipartUploadService;
import com.teamhub.storage.PhotoUploadService;
import com.teamhub.storage.dto.CompleteMultipartRequest;
import com.teamhub.storage.dto.ConfirmUploadRequest;
import com.teamhub.storage.dto.GenerateUploadUrlRequest;
import com.teamhub.storage.dto.JobPhotoResponse;
import com.teamhub.storage.dto.PartUploadUrlResponse;
import com.teamhub.storage.dto.StartMultipartRequest;
import com.teamhub.storage.dto.StartMultipartResponse;
import com.teamhub.storage.dto.UploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
@Tag(name = "jobs", description = "Job CRUD endpoints — PRD §9, §10, §22")
public class JobController {

  private final JobService jobService;

  private final JobNoteService jobNoteService;

  private final PhotoUploadService photoUploadService;

  private final MultipartUploadService multipartUploadService;

  public JobController(
      JobService jobService,
      JobNoteService jobNoteService,
      PhotoUploadService photoUploadService,
      MultipartUploadService multipartUploadService) {
    this.jobService = jobService;
    this.jobNoteService = jobNoteService;
    this.photoUploadService = photoUploadService;
    this.multipartUploadService = multipartUploadService;
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
  public JobResponse get(@PathVariable UUID id, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    return jobService.getById(tenantId, id, actorUserId, actorRole);
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

  /**
   * Starts a job: transitions {@code ASSIGNED_TO_TECHNICIAN → IN_PROGRESS} with sub-status {@code
   * WORKING} (PRD §13, TASK-047).
   *
   * <p>The calling technician must be assigned to the job; otherwise 403 {@code
   * JOB_NOT_ASSIGNED_TO_CALLER}.
   */
  @PostMapping("/{id}/start")
  @PreAuthorize("hasAuthority('PERM_job.read.own')")
  @Operation(
      summary = "Start a job",
      description =
          "Technician transitions the job to IN_PROGRESS / WORKING. Must be the assigned"
              + " technician (PRD §13).")
  public JobResponse start(
      @PathVariable UUID id,
      @RequestBody(required = false) StartJobRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    return jobService.startJob(tenantId, id, actorUserId, actorRole);
  }

  /**
   * Pauses a job: transitions {@code IN_PROGRESS → PENDING} and records the {@link PendingReason}
   * (PRD §9.1, §13, TASK-047).
   *
   * <p>Returns 422 if {@code pendingReason} is absent. Returns 403 if the caller is not the
   * assigned technician.
   */
  @PostMapping("/{id}/pending")
  @PreAuthorize("hasAuthority('PERM_job.read.own')")
  @Operation(
      summary = "Pause a job",
      description =
          "Technician transitions the job to PENDING. pendingReason is required (PRD §9.1).")
  public JobResponse pending(
      @PathVariable UUID id,
      @Valid @RequestBody PendingJobRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    return jobService.pendingJob(
        tenantId, id, request.pendingReason(), request.note(), actorUserId, actorRole);
  }

  /**
   * Marks a job as done pending approval: transitions {@code IN_PROGRESS → DONE_PENDING_APPROVAL}
   * (PRD §9.2, §13, TASK-047).
   *
   * <p>Returns 403 if the caller is not the assigned technician.
   */
  @PostMapping("/{id}/done-pending-approval")
  @PreAuthorize("hasAuthority('PERM_job.read.own')")
  @Operation(
      summary = "Mark a job done pending approval",
      description =
          "Technician signals work is complete; Dispatcher/Admin will approve (PRD §9.2).")
  public JobResponse donePendingApproval(
      @PathVariable UUID id,
      @RequestBody(required = false) DonePendingApprovalRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    String note = request != null ? request.note() : null;
    return jobService.donePendingApproval(tenantId, id, note, actorUserId, actorRole);
  }

  /**
   * Completes a job: transitions {@code IN_PROGRESS → COMPLETED} (PRD §9.3, §13, TASK-047).
   *
   * <p>Only allowed when the tenant's {@code close_jobs_enabled} toggle is on (HTTP 403 {@code
   * CLOSE_JOBS_DISABLED} otherwise). Returns 403 if the caller is not the assigned technician.
   */
  @PostMapping("/{id}/complete")
  @PreAuthorize("hasAuthority('PERM_job.read.own')")
  @Operation(
      summary = "Complete a job",
      description =
          "Technician marks the job COMPLETED. Requires the tenant Close Jobs toggle to be on"
              + " (PRD §9.3, §13).")
  public JobResponse complete(
      @PathVariable UUID id,
      @RequestBody(required = false) CompleteJobRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    String actorRole = extractRole(authentication);
    String note = request != null ? request.note() : null;
    return jobService.completeJob(tenantId, id, note, actorUserId, actorRole);
  }

  // =========================================================================
  // Job notes endpoints (PRD §13.2, §13.4; TASK-048)
  // =========================================================================

  /**
   * Appends an immutable note to the specified job (PRD §13.2, §13.4; TASK-048).
   *
   * <p>Any user with {@code PERM_job.read.own} (Technician, Dispatcher) may append a note. The note
   * cannot be edited or deleted — no PUT/PATCH/DELETE routes are registered.
   */
  @PostMapping("/{id}/notes")
  @PreAuthorize("hasAuthority('PERM_job.read.own') or hasAuthority('PERM_job.read.tenant')")
  @Operation(
      summary = "Add a note to a job",
      description =
          "Appends an immutable note to the job. Notes cannot be edited or deleted (PRD §13.2).")
  public ResponseEntity<JobNoteResponse> addNote(
      @PathVariable UUID id,
      @Valid @RequestBody CreateJobNoteRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    JobNoteResponse response = jobNoteService.addNote(tenantId, id, actorUserId, request.body());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Lists all notes for the specified job in newest-first order (PRD §13.2; TASK-048).
   *
   * <p>Accessible to any caller with {@code PERM_job.read.own} or {@code PERM_job.read.tenant}.
   */
  @GetMapping("/{id}/notes")
  @PreAuthorize("hasAuthority('PERM_job.read.own') or hasAuthority('PERM_job.read.tenant')")
  @Operation(
      summary = "List notes for a job",
      description = "Returns all notes ordered by created_at DESC (PRD §13.2).")
  public List<JobNoteResponse> listNotes(@PathVariable UUID id) {
    UUID tenantId = TenantContext.get();
    return jobNoteService.listNotes(tenantId, id);
  }

  // =========================================================================
  // Photo upload endpoints (PRD §14.2, §14.3; TASK-049)
  // =========================================================================

  /**
   * Generates a pre-signed PUT URL for uploading a photo (PRD §14.2; TASK-049).
   *
   * <p>Allowed content types: image/jpeg, image/png, image/heic, image/webp (PRD §14.3). Returns
   * 422 {@code UNSUPPORTED_MIME} for any other type.
   */
  @PostMapping("/{id}/photo-upload-url")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Generate a pre-signed photo upload URL",
      description =
          "Returns a PUT URL valid for 5 min. Allowed types: image/jpeg, png, heic, webp"
              + " (PRD §14.3).")
  public UploadUrlResponse generatePhotoUploadUrl(
      @PathVariable UUID id,
      @Valid @RequestBody GenerateUploadUrlRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    return photoUploadService.generateUploadUrl(
        tenantId, id, actorUserId, request.contentType(), request.fileExtension());
  }

  /**
   * Confirms a completed photo upload by persisting metadata and publishing a {@code PhotoUploaded}
   * event (PRD §14.2; TASK-049).
   *
   * <p>Returns 422 {@code PHOTO_TOO_LARGE} if {@code sizeBytes} exceeds 20 MB.
   */
  @PostMapping("/{id}/photos")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Confirm a photo upload",
      description = "Persists photo metadata and emits PhotoUploaded event. Max 20 MB (PRD §14.3).")
  public ResponseEntity<JobPhotoResponse> confirmPhotoUpload(
      @PathVariable UUID id,
      @Valid @RequestBody ConfirmUploadRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    JobPhotoResponse response =
        photoUploadService.confirmUpload(tenantId, id, actorUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // =========================================================================
  // Multipart upload endpoints (PRD §14.4; TASK-050)
  // =========================================================================

  /**
   * Starts a multipart upload session for a large photo (PRD §14.4; TASK-050).
   *
   * <p>Returns 201 with an {@code uploadId} and {@code objectKey} the client must use for
   * subsequent part-URL requests.
   */
  @PostMapping("/{id}/photo-multipart/start")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Start a multipart photo upload",
      description =
          "Initiates an S3 multipart upload session. Returns uploadId + objectKey (PRD §14.4).")
  public ResponseEntity<StartMultipartResponse> startMultipartUpload(
      @PathVariable UUID id,
      @Valid @RequestBody StartMultipartRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    StartMultipartResponse response =
        multipartUploadService.startUpload(tenantId, id, actorUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Returns a pre-signed PUT URL for uploading a single part (PRD §14.4; TASK-050).
   *
   * <p>The URL is valid for 15 minutes. The client must upload each part directly to S3 and collect
   * the returned ETag for use in the complete request.
   */
  @PostMapping("/{id}/photo-multipart/{uploadId}/part/{partNumber}")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Get a pre-signed URL for a multipart upload part",
      description = "Returns a 15-min presigned PUT URL for the specified part number (PRD §14.4).")
  public PartUploadUrlResponse getPartUploadUrl(
      @PathVariable UUID id, @PathVariable UUID uploadId, @PathVariable int partNumber) {
    UUID tenantId = TenantContext.get();
    return multipartUploadService.getPartUploadUrl(tenantId, uploadId, partNumber);
  }

  /**
   * Completes a multipart upload by assembling all parts and persisting a {@link
   * com.teamhub.storage.JobPhoto} record (PRD §14.4; TASK-050).
   *
   * <p>Returns 201 with the {@link JobPhotoResponse} for the newly created photo record.
   */
  @PostMapping("/{id}/photo-multipart/{uploadId}/complete")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Complete a multipart photo upload",
      description =
          "Assembles S3 parts, persists photo metadata, emits PhotoUploaded event (PRD §14.4).")
  public ResponseEntity<JobPhotoResponse> completeMultipartUpload(
      @PathVariable UUID id,
      @PathVariable UUID uploadId,
      @RequestBody CompleteMultipartRequest request,
      Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    JobPhotoResponse response =
        multipartUploadService.completeUpload(tenantId, id, actorUserId, uploadId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Aborts a multipart upload, cancelling in-progress part uploads in S3 (PRD §14.4; TASK-050).
   *
   * <p>Returns 204 No Content on success.
   */
  @DeleteMapping("/{id}/photo-multipart/{uploadId}")
  @PreAuthorize("hasAuthority('PERM_photo.upload')")
  @Operation(
      summary = "Abort a multipart photo upload",
      description = "Cancels the S3 multipart upload and marks the session ABORTED (PRD §14.4).")
  public ResponseEntity<Void> abortMultipartUpload(
      @PathVariable UUID id, @PathVariable UUID uploadId) {
    UUID tenantId = TenantContext.get();
    multipartUploadService.abortUpload(tenantId, uploadId);
    return ResponseEntity.noContent().build();
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
