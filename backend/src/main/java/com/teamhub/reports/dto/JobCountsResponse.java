package com.teamhub.reports.dto;

import java.util.List;

/** Response body for job-count aggregation endpoints (PRD §18.1, TASK-067). */
public record JobCountsResponse(List<DayBucket> buckets) {}
