package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an Admin deactivation request would leave the tenant with no active Admin (PRD §5.2,
 * TASK-055).
 */
public class LastAdminProtectedException extends DomainException {

  public static final String CODE = "LAST_ADMIN_PROTECTED";

  public LastAdminProtectedException() {
    super(
        CODE,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Cannot deactivate the last active Admin in the tenant");
  }
}
