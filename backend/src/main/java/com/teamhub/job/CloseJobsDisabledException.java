package com.teamhub.job;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Technician attempts the {@code IN_PROGRESS → COMPLETED} transition but the tenant's
 * {@code close_jobs_enabled} flag is {@code false} (PRD §9.3, §13; TASK-053).
 *
 * <p>HTTP 403 is returned so the mobile client can immediately hide the Complete button without
 * retrying (PRD §15.2).
 */
public class CloseJobsDisabledException extends DomainException {

  public static final String CODE = "CLOSE_JOBS_DISABLED";

  public CloseJobsDisabledException() {
    super(
        CODE,
        HttpStatus.FORBIDDEN,
        "The tenant has not enabled the Close Jobs feature."
            + " An Admin must enable it before Technicians can mark jobs Completed.");
  }
}
