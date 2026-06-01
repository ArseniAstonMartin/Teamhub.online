package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code JobService} when a job is created and enters the {@code SUBMITTED}
 * state (PRD §22.5.5).
 *
 * <p>Consumer groups: {@code quota-checker}, {@code push-fan-out-technicians}, {@code
 * escalation-scheduler}, {@code audit-logger}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobSubmitted(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("service_type") String serviceType,
    @JsonProperty("scheduled_at") Instant scheduledAt) {}
