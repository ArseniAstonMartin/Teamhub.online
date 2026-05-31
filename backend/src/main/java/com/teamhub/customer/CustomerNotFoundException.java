package com.teamhub.customer;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Thrown when a customer lookup by ID returns no result for the current tenant (PRD §11.3). */
public final class CustomerNotFoundException extends DomainException {

  public static final String CODE = "CUSTOMER_NOT_FOUND";

  public CustomerNotFoundException(UUID id) {
    super(CODE, HttpStatus.NOT_FOUND, "Customer not found: " + id);
  }
}
