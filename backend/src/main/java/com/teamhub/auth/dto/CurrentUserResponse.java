package com.teamhub.auth.dto;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import java.time.Instant;
import java.util.UUID;

/** Profile projection for the authenticated caller ({@code GET /api/v1/users/me}, TASK-132). */
public record CurrentUserResponse(
    UUID id, String email, String name, UserRole role, UUID tenantId, Instant lastLoginAt) {

  /** Builds a {@link CurrentUserResponse} from a {@link User} entity. */
  public static CurrentUserResponse from(User user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRole(),
        user.getTenant().getId(),
        user.getLastLoginAt());
  }
}
