package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Google OAuth2 login attempts to create an account for an email that has no pending
 * invite (PRD §6.1, TASK-021).
 *
 * <p>Teamhub does not auto-create accounts from OAuth2 logins; an Admin must invite the user first.
 */
public class NoInviteForEmailException extends DomainException {

  public static final String CODE = "NO_INVITE_FOR_EMAIL";

  public NoInviteForEmailException() {
    super(CODE, HttpStatus.FORBIDDEN, "No pending invite found for this email address.");
  }
}
