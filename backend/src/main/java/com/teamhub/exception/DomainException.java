package com.teamhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all typed domain exceptions (PRD §23.4).
 *
 * <p>Each subclass carries a machine-readable {@code code} (e.g. {@code INVALID_CREDENTIALS},
 * {@code JOB_ALREADY_CLAIMED}) and the HTTP status that the {@link GlobalExceptionHandler} uses to
 * build the uniform error envelope. Subclasses should declare a {@code code} constant and call
 * {@link #DomainException(String, HttpStatus, String)} from their own constructors.
 *
 * <p>Stack traces are never exposed in HTTP responses; the {@link GlobalExceptionHandler} logs them
 * at ERROR level with a {@code request_id} for 5xx paths.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  private final HttpStatus httpStatus;

  /**
   * @param code machine-readable error code (e.g. {@code "INVALID_CREDENTIALS"})
   * @param httpStatus the HTTP status code that the error envelope should carry
   * @param message human-readable message (not exposed beyond the response body)
   */
  protected DomainException(String code, HttpStatus httpStatus, String message) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /** Machine-readable error code used as {@code error.code} in the response envelope. */
  public String getCode() {
    return code;
  }

  /** HTTP status that the response should carry. */
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
