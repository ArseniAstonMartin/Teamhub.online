package com.teamhub.tenancy;

/** Thrown when tenant provisioning cannot be completed due to a domain constraint. */
public class TenantProvisionException extends RuntimeException {

  public TenantProvisionException(String message) {
    super(message);
  }
}
