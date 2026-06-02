package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an invite token is not found, has already been accepted, or has expired (PRD §6.5,
 * TASK-054).
 */
public class InviteUsedOrExpiredException extends DomainException {

  public static final String CODE = "INVITE_USED_OR_EXPIRED";

  public InviteUsedOrExpiredException() {
    super(
        CODE,
        HttpStatus.BAD_REQUEST,
        "Invite token is invalid, expired, or has already been used.");
  }
}
