package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code JobService} when a job transitions to the {@code COMPLETED} state (PRD
 * §22.5.5).
 *
 * <p>Consumer groups: {@code audit-logger}, {@code analytics-aggregator}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobCompleted(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("technician_id") UUID technicianId,
    @JsonProperty("completed_at") Instant completedAt) {}
