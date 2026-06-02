package com.teamhub.auth.dto;

import com.teamhub.auth.Invite;
import java.time.Instant;
import java.util.UUID;

/** Response body for a created or fetched invite (PRD §6.5, TASK-054). */
public record InviteResponse(
    UUID id, String email, String role, Instant expiresAt, Instant createdAt) {

  public static InviteResponse from(Invite invite) {
    return new InviteResponse(
        invite.getId(),
        invite.getEmail(),
        invite.getRole(),
        invite.getExpiresAt(),
        invite.getCreatedAt());
  }
}
