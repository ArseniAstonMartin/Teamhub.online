package com.teamhub.auth;

/** Thrown when a login attempt fails due to unknown email, wrong password, or inactive account. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
