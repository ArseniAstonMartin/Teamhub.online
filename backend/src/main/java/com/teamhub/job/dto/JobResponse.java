package com.teamhub.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamhub.job.CancellationReason;
import com.teamhub.job.InProgressSubstatus;
import com.teamhub.job.Job;
import com.teamhub.job.JobStatus;
import com.teamhub.job.PendingReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for job endpoints (PRD §9, §10, §22, TASK-044).
 *
 * <p>Null-valued optional fields are omitted from the JSON response via {@link
 * JsonInclude.Include#NON_NULL}.
 *
 * <p>{@code customerPhone} follows the shared-pool privacy rule (PRD §10.1): it is present only
 * when the job has been claimed (status ≠ {@code SUBMITTED}). Callers reading an unclaimed job
 * receive {@code null} for this field so that phone numbers are not leaked to technicians who have
 * not yet won the claim race.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
    UUID id,
    UUID tenantId,
    UUID customerId,
    UUID assignedTechnicianId,
    UUID appointmentSchemaId,
    JobStatus status,
    InProgressSubstatus inProgressSubstatus,
    PendingReason pendingReason,
    CancellationReason cancellationReason,
    Map<String, Object> customFields,
    Double lat,
    Double lon,
    Instant scheduledStart,
    Instant scheduledEnd,
    Instant actualStart,
    Instant actualEnd,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt,
    String customerPhone,
    String customerName,
    String assigneeName) {

  /** Builds a {@link JobResponse} from a {@link Job} entity without enrichment. */
  public static JobResponse from(Job job) {
    return from(job, null, null, null);
  }

  /**
   * Builds a {@link JobResponse} from a {@link Job} entity, applying phone masking per PRD §10.1.
   *
   * <p>{@code customerPhone} is set to {@code null} whenever {@code job.status == SUBMITTED},
   * regardless of the value passed in, so that unclaimed-job responses never leak the phone number.
   *
   * @param job the job entity
   * @param customerPhone the raw phone from the linked {@link com.teamhub.customer.Customer}; may
   *     be {@code null}
   */
  public static JobResponse from(Job job, String customerPhone) {
    return from(job, customerPhone, null, null);
  }

  /**
   * Builds a fully enriched {@link JobResponse} (TASK-130, PRD §11).
   *
   * <p>{@code customerName} and {@code assigneeName} are omitted from JSON when {@code null} via
   * the record-level {@link JsonInclude} annotation.
   */
  public static JobResponse from(
      Job job, String customerPhone, String customerName, String assigneeName) {
    Double lon = job.getLocation() != null ? job.getLocation().getX() : null;
    Double lat = job.getLocation() != null ? job.getLocation().getY() : null;
    String maskedPhone = job.getStatus() == JobStatus.SUBMITTED ? null : customerPhone;
    return new JobResponse(
        job.getId(),
        job.getTenantId(),
        job.getCustomerId(),
        job.getAssignedTechnicianId(),
        job.getAppointmentSchemaId(),
        job.getStatus(),
        job.getInProgressSubstatus(),
        job.getPendingReason(),
        job.getCancellationReason(),
        job.getCustomFields(),
        lat,
        lon,
        job.getScheduledStart(),
        job.getScheduledEnd(),
        job.getActualStart(),
        job.getActualEnd(),
        job.getCreatedAt(),
        job.getUpdatedAt(),
        job.getArchivedAt(),
        maskedPhone,
        customerName,
        assigneeName);
  }
}
