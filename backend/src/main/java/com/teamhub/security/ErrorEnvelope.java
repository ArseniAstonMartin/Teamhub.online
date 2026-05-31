package com.teamhub.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Uniform error envelope as defined by PRD §23.4:
 *
 * <pre>{ "error": { "code": "...", "message": "...", "details": { ... } } }</pre>
 *
 * <p>Centralized in TASK-025: the {@code details} field carries supplementary data (field-level
 * validation errors, request_id for 500s, domain-specific context). It is omitted from the JSON
 * response when null so authorization failures continue to emit only {@code code} and {@code
 * message}.
 */
public record ErrorEnvelope(ErrorBody error) {

  /** The inner error object of the §23.4 envelope. {@code details} is omitted when null. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorBody(String code, String message, Map<String, Object> details) {}

  /**
   * Builds an envelope without supplementary details (used for 401/403 filter-layer responses).
   *
   * @param code machine-readable error code
   * @param message human-readable message
   * @return the envelope
   */
  public static ErrorEnvelope of(String code, String message) {
    return new ErrorEnvelope(new ErrorBody(code, message, null));
  }

  /**
   * Builds an envelope with supplementary details.
   *
   * @param code machine-readable error code
   * @param message human-readable message
   * @param details optional key-value supplementary data (field errors, request_id, etc.)
   * @return the envelope
   */
  public static ErrorEnvelope of(String code, String message, Map<String, Object> details) {
    return new ErrorEnvelope(new ErrorBody(code, message, details));
  }
}
