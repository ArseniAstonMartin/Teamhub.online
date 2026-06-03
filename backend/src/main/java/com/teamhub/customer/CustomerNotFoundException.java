package com.teamhub.customer;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a customer is not found by ID within the requesting tenant (TASK-071, PRD §24.5).
 *
 * <p>HTTP 404 Not Found; machine-readable code {@value #CODE}.
 */
public class CustomerNotFoundException extends DomainException {

  public static final String CODE = "CUSTOMER_NOT_FOUND";

  public CustomerNotFoundException(UUID customerId) {
    super(CODE, HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
  }
}
