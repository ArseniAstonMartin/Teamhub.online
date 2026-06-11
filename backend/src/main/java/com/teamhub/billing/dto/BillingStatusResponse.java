package com.teamhub.billing.dto;

import com.teamhub.tenancy.Plan;
import java.time.Instant;

/**
 * Response body for GET /api/v1/billing/status (PRD §17, TASK-125).
 *
 * <p>Plan limits (jobs/month, seats) are derived on the client via {@code PLAN_LIMITS}; this
 * payload carries live usage counters and subscription state only.
 */
public record BillingStatusResponse(
    Plan plan,
    Instant periodEnd,
    boolean paymentFailed,
    Instant paymentFailedAt,
    int jobsThisPeriod,
    int seats) {}
