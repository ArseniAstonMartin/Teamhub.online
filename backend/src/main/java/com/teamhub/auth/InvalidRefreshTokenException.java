package com.teamhub.auth;

/** Thrown when a presented refresh token is unknown, expired, or already revoked. */
public class InvalidRefreshTokenException extends RuntimeException {

  public InvalidRefreshTokenException() {
    super("Refresh token is invalid, expired, or revoked");
  }
}
