package com.teamhub.storage;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is attempted on a multipart upload that is already completed (TASK-050).
 *
 * <p>HTTP 409 Conflict; machine-readable code {@value #CODE}.
 */
public class MultipartUploadAlreadyCompletedException extends DomainException {

  public static final String CODE = "MULTIPART_UPLOAD_ALREADY_COMPLETED";

  public MultipartUploadAlreadyCompletedException(java.util.UUID uploadId) {
    super(
        CODE,
        HttpStatus.CONFLICT,
        "Multipart upload '%s' is not in IN_PROGRESS status.".formatted(uploadId));
  }
}
