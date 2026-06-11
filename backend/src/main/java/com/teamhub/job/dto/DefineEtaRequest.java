package com.teamhub.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/eta} (PRD §13.2; TASK-135).
 *
 * <p>Range matches mobile {@code DefineEtaSheet}: 5–480 minutes.
 */
public record DefineEtaRequest(@NotNull @Min(5) @Max(480) Integer etaMinutes) {}
