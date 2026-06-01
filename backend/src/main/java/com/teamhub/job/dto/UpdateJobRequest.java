package com.teamhub.job.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/jobs/{id}} (PRD §22, TASK-044).
 *
 * <p>All fields are optional (nullable). Only non-null fields overwrite the stored values — status
 * changes must go through the {@code /transition} endpoint (state machine), not through PATCH.
 */
public record UpdateJobRequest(
    UUID customerId,
    UUID appointmentSchemaId,
    OffsetDateTime scheduledStart,
    OffsetDateTime scheduledEnd,
    Double lat,
    Double lon,
    Map<String, Object> customFields,
    String note) {}
