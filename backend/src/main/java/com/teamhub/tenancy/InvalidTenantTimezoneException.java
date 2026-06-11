package com.teamhub.tenancy;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a tenant settings PATCH carries a time zone outside the allowed US set. */
public class InvalidTenantTimezoneException extends DomainException {

  public static final String CODE = "INVALID_TENANT_TIMEZONE";

  public InvalidTenantTimezoneException(String timezone) {
    super(CODE, HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported tenant timezone: " + timezone);
  }
}
