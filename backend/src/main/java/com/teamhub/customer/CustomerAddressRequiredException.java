package com.teamhub.customer;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a customer cannot be persisted because no service-address coordinates are available.
 */
public class CustomerAddressRequiredException extends DomainException {

  public static final String CODE = "CUSTOMER_ADDRESS_REQUIRED";

  public CustomerAddressRequiredException() {
    super(
        CODE,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Customer address with coordinates is required. Provide lat/lon or a geocodable address.");
  }
}
