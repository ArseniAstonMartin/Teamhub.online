package com.teamhub.auth;

/** Thrown when a user with the given email already exists. */
public class UserAlreadyExistsException extends RuntimeException {

  public UserAlreadyExistsException(String email) {
    super("User already exists with email: " + email);
  }
}
