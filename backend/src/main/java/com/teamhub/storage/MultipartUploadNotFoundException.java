package com.teamhub.storage;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a multipart upload session is not found for the given ID and tenant (TASK-050).
 *
 * <p>HTTP 404 Not Found; machine-readable code {@value #CODE}.
 */
public class MultipartUploadNotFoundException extends DomainException {

  public static final String CODE = "MULTIPART_UPLOAD_NOT_FOUND";

  public MultipartUploadNotFoundException(java.util.UUID uploadId) {
    super(CODE, HttpStatus.NOT_FOUND, "Multipart upload '%s' not found.".formatted(uploadId));
  }
}
