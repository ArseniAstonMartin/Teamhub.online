package com.teamhub.job.dto;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/done-pending-approval} (PRD §9.2, §13, TASK-047).
 *
 * <p>Optional {@code note} is stored in the status-history row.
 */
public record DonePendingApprovalRequest(String note) {}
