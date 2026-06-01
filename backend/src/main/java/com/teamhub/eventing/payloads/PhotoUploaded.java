package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code JobPhotoService} after a photo upload is confirmed (PRD §22.5.5).
 *
 * <p>Positions the system for WebSocket push in v1.2; v1.0 web side polls.
 *
 * <p>Consumer groups: {@code web-realtime-notifier}, {@code audit-logger}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PhotoUploaded(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("photo_id") UUID photoId,
    @JsonProperty("photo_url") String photoUrl,
    @JsonProperty("uploaded_by") UUID uploadedBy) {}
