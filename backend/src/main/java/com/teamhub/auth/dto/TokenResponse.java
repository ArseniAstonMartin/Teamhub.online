package com.teamhub.auth.dto;

import com.teamhub.auth.AuthService;
import java.time.Duration;
import java.time.Instant;

/**
 * Token pair returned by login and refresh.
 *
 * @param accessToken the signed RS256 access token
 * @param refreshToken the opaque refresh token (store securely; sent on refresh/logout)
 * @param tokenType always {@code Bearer}
 * @param expiresIn access-token lifetime in seconds
 */
public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {

  public static TokenResponse from(AuthService.IssuedTokens tokens) {
    long expiresIn = Duration.between(Instant.now(), tokens.accessTokenExpiresAt()).getSeconds();
    return new TokenResponse(
        tokens.accessToken(), tokens.refreshToken(), "Bearer", Math.max(expiresIn, 0));
  }
}
