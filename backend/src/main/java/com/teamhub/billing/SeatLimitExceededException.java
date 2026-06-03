package com.teamhub.billing;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant has reached the maximum number of seats allowed by its plan (PRD §17.3,
 * TASK-064).
 *
 * <p>Maps to HTTP 402 Payment Required with code {@code SEAT_LIMIT_EXCEEDED}.
 */
public class SeatLimitExceededException extends DomainException {

  public static final String CODE = "SEAT_LIMIT_EXCEEDED";

  public SeatLimitExceededException() {
    super(CODE, HttpStatus.PAYMENT_REQUIRED, "Seat limit exceeded for this plan");
  }
}
