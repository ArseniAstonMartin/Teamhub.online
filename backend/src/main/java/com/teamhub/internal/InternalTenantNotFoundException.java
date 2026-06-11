package com.teamhub.internal;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Thrown when an internal route references a tenant that does not exist. */
public class InternalTenantNotFoundException extends DomainException {

  public static final String CODE = "TENANT_NOT_FOUND";

  public InternalTenantNotFoundException(UUID tenantId) {
    super(CODE, HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
  }
}
