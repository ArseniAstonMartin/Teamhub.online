package com.teamhub.job.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/jobs} (PRD §22, TASK-044).
 *
 * <p>{@code customerId} and {@code scheduledStart} are required; all other fields are optional for
 * MVP (e.g. {@code appointmentSchemaId} may be null when no schema is configured for the tenant).
 * Custom-field validation against the referenced schema is performed inside {@link
 * com.teamhub.job.JobService} via {@link com.teamhub.job.JobCustomFieldValidator}.
 */
public record CreateJobRequest(
    @NotNull UUID customerId,
    UUID appointmentSchemaId,
    @NotNull OffsetDateTime scheduledStart,
    OffsetDateTime scheduledEnd,
    Double lat,
    Double lon,
    Map<String, Object> customFields,
    String note) {}
