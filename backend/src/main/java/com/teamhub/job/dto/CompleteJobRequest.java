package com.teamhub.job.dto;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/complete} (PRD §9.3, §13, TASK-047).
 *
 * <p>Optional {@code note} is stored in the status-history row. The transition is only allowed when
 * the tenant's {@code close_jobs_enabled} toggle is on (enforced by {@link
 * com.teamhub.job.JobStateMachineService}).
 */
public record CompleteJobRequest(String note) {}
