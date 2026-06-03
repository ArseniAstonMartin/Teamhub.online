package com.teamhub.billing;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant on the Free tier attempts to use a feature locked behind a paid plan (PRD
 * §17.3, TASK-064).
 *
 * <p>Maps to HTTP 402 Payment Required with code {@code PLAN_FEATURE_LOCKED}.
 */
public class PlanFeatureLockedException extends DomainException {

  public static final String CODE = "PLAN_FEATURE_LOCKED";

  public PlanFeatureLockedException(String featureName) {
    super(
        CODE,
        HttpStatus.PAYMENT_REQUIRED,
        "Feature '" + featureName + "' is not available on this plan");
  }
}
