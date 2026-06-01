package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by {@code UserService} when a new user is invited to a tenant (PRD §22.5.5).
 *
 * <p>Carries the signed invite token required for the email link.
 *
 * <p>Consumer groups: {@code email-sender}, {@code audit-logger}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInvited(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("invited_user_email") String invitedUserEmail,
    @JsonProperty("invited_by") UUID invitedBy,
    @JsonProperty("role") String role,
    @JsonProperty("invite_token") String inviteToken,
    @JsonProperty("expires_at") Instant expiresAt) {}
