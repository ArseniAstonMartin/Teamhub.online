package com.teamhub.reports.dto;

/**
 * Average time-to-claim and time-to-completion report (PRD §18.1, TASK-067).
 *
 * <p>Both values are in seconds. A {@code null} value means no qualifying transitions exist for the
 * requested date range.
 */
public record AvgTimesResponse(Double avgTimeToClaimSeconds, Double avgTimeToCompletionSeconds) {}
