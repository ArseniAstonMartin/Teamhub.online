package com.teamhub.auth.dto;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import java.time.Instant;
import java.util.UUID;

/** Read-only projection of a {@link User} returned by the user-management API (TASK-055). */
public record UserResponse(
    UUID id,
    String email,
    UserRole role,
    Instant deactivatedAt,
    Instant createdAt,
    Instant lastLoginAt) {

  /** Builds a {@link UserResponse} from a {@link User} entity. */
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getRole(),
        user.getDeactivatedAt(),
        user.getCreatedAt(),
        user.getLastLoginAt());
  }
}
