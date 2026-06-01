package com.teamhub.job;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a job is not found by ID (TASK-043).
 *
 * <p>HTTP 404 Not Found; machine-readable code {@value #CODE}.
 */
public class JobNotFoundException extends DomainException {

  public static final String CODE = "JOB_NOT_FOUND";

  public JobNotFoundException(UUID jobId) {
    super(CODE, HttpStatus.NOT_FOUND, "Job not found: " + jobId);
  }
}
