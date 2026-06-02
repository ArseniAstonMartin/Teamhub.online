package com.teamhub.job.dto;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/start} (PRD §13, TASK-047).
 *
 * <p>No required fields — the endpoint always sets sub-status to {@code WORKING}.
 */
public record StartJobRequest() {}
