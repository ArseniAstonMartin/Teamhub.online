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
 * Response DTO for job endpoints (PRD §9, §22, TASK-044).
 *
 * <p>Null-valued optional fields are omitted from the JSON response via {@link
 * JsonInclude.Include#NON_NULL}.
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
    Instant archivedAt) {

  /** Builds a {@link JobResponse} from a {@link Job} entity. */
  public static JobResponse from(Job job) {
    Double lon = job.getLocation() != null ? job.getLocation().getX() : null;
    Double lat = job.getLocation() != null ? job.getLocation().getY() : null;
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
        job.getArchivedAt());
  }
}
