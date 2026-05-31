package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a login attempt fails due to unknown email, wrong password, or inactive account. */
public class InvalidCredentialsException extends DomainException {

  public static final String CODE = "INVALID_CREDENTIALS";

  public InvalidCredentialsException() {
    super(CODE, HttpStatus.UNAUTHORIZED, "Invalid email or password");
  }
}
