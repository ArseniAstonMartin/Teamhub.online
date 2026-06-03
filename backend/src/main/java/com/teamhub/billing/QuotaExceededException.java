package com.teamhub.billing;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant has exhausted its monthly job quota (PRD §17.3, TASK-064).
 *
 * <p>Maps to HTTP 402 Payment Required with code {@code QUOTA_EXCEEDED}.
 */
public class QuotaExceededException extends DomainException {

  public static final String CODE = "QUOTA_EXCEEDED";

  public QuotaExceededException() {
    super(CODE, HttpStatus.PAYMENT_REQUIRED, "Monthly job quota exceeded for this plan");
  }
}
