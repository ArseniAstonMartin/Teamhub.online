package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Thrown when a referenced user does not exist within the tenant. */
public class UserNotFoundException extends DomainException {

  public static final String CODE = "USER_NOT_FOUND";

  public UserNotFoundException(UUID userId) {
    super(CODE, HttpStatus.NOT_FOUND, "User not found: " + userId);
  }
}
