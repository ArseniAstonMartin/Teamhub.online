package com.teamhub.storage;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a client requests a pre-signed URL for a content type not in the allow-list (PRD
 * §14.3; TASK-049).
 *
 * <p>Allowed types: {@code image/jpeg}, {@code image/png}, {@code image/heic}, {@code image/webp}.
 *
 * <p>HTTP 422 Unprocessable Entity; machine-readable code {@value #CODE}.
 */
public class UnsupportedMimeTypeException extends DomainException {

  public static final String CODE = "UNSUPPORTED_MIME";

  public UnsupportedMimeTypeException(String contentType) {
    super(
        CODE,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Content type '%s' is not allowed. Supported types: image/jpeg, image/png, image/heic, image/webp."
            .formatted(contentType));
  }
}
