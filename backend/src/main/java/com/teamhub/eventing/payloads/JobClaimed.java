package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code JobService} when a technician successfully claims a job (PRD §22.5.5).
 *
 * <p>Consumer groups: {@code silent-push-other-techs}, {@code audit-logger}, {@code
 * escalation-canceler}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobClaimed(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("technician_id") UUID technicianId,
    @JsonProperty("claimed_at") Instant claimedAt) {}
