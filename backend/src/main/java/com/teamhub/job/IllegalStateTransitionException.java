package com.teamhub.job;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a caller attempts a job status transition that is not allowed by the PRD §9.2
 * transition table, or when the actor's role is not permitted to perform an otherwise valid
 * transition.
 *
 * <p>HTTP 409 Conflict; machine-readable code {@value #CODE}.
 */
public class IllegalStateTransitionException extends DomainException {

  public static final String CODE = "INVALID_TRANSITION";

  /**
   * @param fromStatus the current status of the job (may be {@code null} for initial transitions)
   * @param toStatus the target status that was requested
   */
  public IllegalStateTransitionException(JobStatus fromStatus, JobStatus toStatus) {
    super(
        CODE,
        HttpStatus.CONFLICT,
        "Transition from "
            + (fromStatus == null ? "null" : fromStatus.name())
            + " to "
            + toStatus.name()
            + " is not allowed");
  }
}
