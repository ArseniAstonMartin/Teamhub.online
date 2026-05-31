package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a presented refresh token is unknown, expired, or already revoked. */
public class InvalidRefreshTokenException extends DomainException {

  public static final String CODE = "INVALID_REFRESH_TOKEN";

  public InvalidRefreshTokenException() {
    super(CODE, HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired, or revoked");
  }
}
