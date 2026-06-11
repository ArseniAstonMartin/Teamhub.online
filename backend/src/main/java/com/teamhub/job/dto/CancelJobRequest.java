package com.teamhub.job.dto;

import com.teamhub.job.CancellationReason;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /api/v1/jobs/{id}/cancel} (PRD §9.1, §9.4; TASK-127). */
public record CancelJobRequest(@NotNull CancellationReason reason) {}
