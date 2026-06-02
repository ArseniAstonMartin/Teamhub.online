package com.teamhub.storage;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the confirmed photo size exceeds the 20 MB limit (PRD §14.3; TASK-049).
 *
 * <p>HTTP 422 Unprocessable Entity; machine-readable code {@value #CODE}.
 */
public class PhotoTooLargeException extends DomainException {

  public static final String CODE = "PHOTO_TOO_LARGE";

  private static final long MAX_BYTES = 20L * 1024 * 1024;

  public PhotoTooLargeException(long sizeBytes) {
    super(
        CODE,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Photo size %d bytes exceeds the maximum allowed %d bytes (20 MB)."
            .formatted(sizeBytes, MAX_BYTES));
  }
}
