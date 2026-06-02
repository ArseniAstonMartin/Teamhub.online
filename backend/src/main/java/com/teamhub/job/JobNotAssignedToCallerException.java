package com.teamhub.job;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Technician attempts a transition on a job that is not assigned to them (PRD §13,
 * TASK-047).
 *
 * <p>HTTP 403 is returned so the mobile client can prevent the action without retrying.
 */
public class JobNotAssignedToCallerException extends DomainException {

  public static final String CODE = "JOB_NOT_ASSIGNED_TO_CALLER";

  public JobNotAssignedToCallerException(UUID jobId) {
    super(CODE, HttpStatus.FORBIDDEN, "You are not assigned to job " + jobId + ".");
  }
}
