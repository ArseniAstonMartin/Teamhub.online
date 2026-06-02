package com.teamhub.job.dto;

import com.teamhub.job.PendingReason;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/pending} (PRD §9.1, §13, TASK-047).
 *
 * <p>{@code pendingReason} is required (422 if absent). {@code note} is optional.
 */
public record PendingJobRequest(@NotNull PendingReason pendingReason, String note) {}
