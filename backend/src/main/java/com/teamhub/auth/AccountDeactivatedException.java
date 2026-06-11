package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a deactivated user attempts an authenticated action (TASK-132). */
public class AccountDeactivatedException extends DomainException {

  public static final String CODE = "ACCOUNT_DEACTIVATED";

  public AccountDeactivatedException() {
    super(CODE, HttpStatus.FORBIDDEN, "This account has been deactivated.");
  }
}
