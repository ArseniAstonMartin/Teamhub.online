package com.teamhub.billing;

import com.stripe.exception.StripeException;
import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Stripe API call fails unexpectedly, wrapping the underlying {@link StripeException}
 * as the cause (PRD §17.2, TASK-061).
 *
 * <p>Maps to HTTP 502 Bad Gateway — the upstream Stripe API is the failing dependency.
 */
public class BillingServiceException extends DomainException {

  public static final String CODE = "STRIPE_ERROR";

  public BillingServiceException(String message, StripeException cause) {
    super(CODE, HttpStatus.BAD_GATEWAY, message);
    initCause(cause);
  }

  /** Thrown when the precondition check fails without an underlying Stripe API call. */
  public BillingServiceException(String message) {
    super(CODE, HttpStatus.BAD_GATEWAY, message);
  }
}
