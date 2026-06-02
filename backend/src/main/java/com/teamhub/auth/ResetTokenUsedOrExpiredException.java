package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a password-reset token is not found, has already been used, or has expired (PRD §6.3,
 * TASK-020).
 */
public class ResetTokenUsedOrExpiredException extends DomainException {

  public static final String CODE = "RESET_TOKEN_USED_OR_EXPIRED";

  public ResetTokenUsedOrExpiredException() {
    super(
        CODE, HttpStatus.BAD_REQUEST, "Reset token is invalid, expired, or has already been used.");
  }
}
