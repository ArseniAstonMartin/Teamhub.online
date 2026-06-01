package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by the {@code quota-checker} consumer (a cascading event) when a tenant reaches
 * 80% or 100% of their monthly job quota (PRD §22.5.5).
 *
 * <p>Consumer groups: {@code email-admin}, {@code in-app-banner}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotaThresholdReached(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("threshold_pct") Integer thresholdPct,
    @JsonProperty("current_count") Integer currentCount,
    @JsonProperty("limit") Integer limit) {}
