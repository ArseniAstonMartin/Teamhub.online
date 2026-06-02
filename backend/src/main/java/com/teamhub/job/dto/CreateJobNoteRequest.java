package com.teamhub.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/notes} (PRD §13.2; TASK-048).
 *
 * <p>{@code body} is required and must not exceed 4 000 characters (mirrored by the DB CHECK
 * constraint in V16).
 */
public record CreateJobNoteRequest(@NotBlank @Size(max = 4000) String body) {}
