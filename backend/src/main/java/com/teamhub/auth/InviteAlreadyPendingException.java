package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an active (non-accepted, non-expired) invite already exists for the same email +
 * tenant combination (PRD §6.5, TASK-054).
 */
public class InviteAlreadyPendingException extends DomainException {

  public static final String CODE = "INVITE_ALREADY_PENDING";

  public InviteAlreadyPendingException() {
    super(
        CODE,
        HttpStatus.CONFLICT,
        "An active invite already exists for this email. Use the resend endpoint to resend it.");
  }
}
