package com.teamhub.internal.dto;

import com.teamhub.internal.StaffAuthService;
import java.time.Duration;
import java.time.Instant;

/** Staff access token returned by internal login (no refresh token). */
public record StaffTokenResponse(String accessToken, String tokenType, long expiresIn) {

  public static StaffTokenResponse from(StaffAuthService.StaffSession session) {
    long expiresIn = Duration.between(Instant.now(), session.expiresAt()).getSeconds();
    return new StaffTokenResponse(session.accessToken(), "Bearer", Math.max(expiresIn, 0));
  }
}
