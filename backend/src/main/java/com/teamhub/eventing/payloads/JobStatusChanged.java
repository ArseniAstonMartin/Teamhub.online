package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code JobService} on every job status transition (PRD §22.5.5).
 *
 * <p>Carries the from/to status pair and the actor who triggered the change.
 *
 * <p>Consumer groups: {@code push-actor}, {@code audit-logger}, {@code analytics-aggregator}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusChanged(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("old_status") String oldStatus,
    @JsonProperty("new_status") String newStatus,
    @JsonProperty("actor_id") UUID actorId) {}
