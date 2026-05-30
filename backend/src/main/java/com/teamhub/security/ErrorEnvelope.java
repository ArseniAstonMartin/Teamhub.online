package com.teamhub.security;

/**
 * Uniform error envelope as defined by PRD §23.4:
 *
 * <pre>{ "error": { "code": "...", "message": "...", "details": { ... } } }</pre>
 *
 * <p>Only {@code code} and {@code message} are emitted here; {@code details} is optional in §23.4
 * and is not needed for authorization failures. TO-DO (TASK-025): centralize this envelope in a
 * single {@code @RestControllerAdvice} that also populates {@code details} and reconciles the
 * legacy flat 401 body emitted by {@code AuthExceptionHandler}.
 */
public record ErrorEnvelope(ErrorBody error) {

  /** The inner error object of the §23.4 envelope. */
  public record ErrorBody(String code, String message) {}

  /**
   * Builds an envelope with the given machine-readable code and human-readable message.
   *
   * @param code machine-readable error code
   * @param message human-readable message
   * @return the envelope
   */
  public static ErrorEnvelope of(String code, String message) {
    return new ErrorEnvelope(new ErrorBody(code, message));
  }
}
