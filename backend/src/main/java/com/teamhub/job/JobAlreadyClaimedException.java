package com.teamhub.job;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a technician attempts to claim a job that is no longer in {@code SUBMITTED} state, or
 * when a concurrent claim races ahead and wins the optimistic-lock UPDATE (PRD §10.2).
 *
 * <p>HTTP 409 Conflict; machine-readable code {@value #CODE}.
 */
public class JobAlreadyClaimedException extends DomainException {

  public static final String CODE = "JOB_ALREADY_CLAIMED";

  /**
   * @param jobId the UUID of the contested job
   */
  public JobAlreadyClaimedException(UUID jobId) {
    super(
        CODE,
        HttpStatus.CONFLICT,
        "Job " + jobId + " is already claimed or is not in SUBMITTED state");
  }
}
