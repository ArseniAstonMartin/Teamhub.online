package com.teamhub.internal;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when no pending owner (ADMIN) invite exists for resend. */
public class InternalNoPendingOwnerInviteException extends DomainException {

  public static final String CODE = "NO_PENDING_OWNER_INVITE";

  public InternalNoPendingOwnerInviteException() {
    super(CODE, HttpStatus.NOT_FOUND, "No pending owner invite for this tenant");
  }
}
