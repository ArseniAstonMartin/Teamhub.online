package com.teamhub.exception;

import com.teamhub.auth.RateLimitExceededException;
import com.teamhub.security.ErrorEnvelope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception → {@link ErrorEnvelope} mapper (PRD §23.4, TASK-025).
 *
 * <p>Handles three categories:
 *
 * <ol>
 *   <li><b>Validation failures</b> ({@link MethodArgumentNotValidException}): 422 Unprocessable
 *       Entity with per-field errors aggregated under {@code details.fields}.
 *   <li><b>Domain exceptions</b> ({@link DomainException}): status and code come from the exception
 *       itself, so adding a new error type requires only a new subclass — no handler changes.
 *   <li><b>Unexpected errors</b> ({@link Exception}): 500 Internal Server Error. A unique {@code
 *       request_id} is generated, logged at ERROR level together with the full stack trace; the
 *       response body contains only the {@code request_id} — no stack trace is ever exposed.
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Machine-readable code for bean-validation failures. */
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

  /** Machine-readable code for unexpected server errors. */
  public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

  /**
   * Maps bean-validation failures to 422 with per-field error details.
   *
   * <p>Field errors are aggregated under {@code details.fields} as a list of {@code {field,
   * message}} objects.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field", fe.getField(), "message", defaultMessage(fe.getDefaultMessage())))
            .toList();
    Map<String, Object> details = Map.of("fields", fieldErrors);
    ErrorEnvelope body = ErrorEnvelope.of(VALIDATION_FAILED, "Request validation failed.", details);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  /**
   * Maps any {@link DomainException} to the HTTP status and machine-readable code declared by the
   * exception subclass.
   *
   * <p>For {@link RateLimitExceededException} a {@code Retry-After} response header is added with
   * the number of seconds the client should wait (PRD §6.3).
   */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorEnvelope> handleDomain(DomainException ex) {
    ErrorEnvelope body = ErrorEnvelope.of(ex.getCode(), ex.getMessage());
    if (ex instanceof RateLimitExceededException rle) {
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(rle.getRetryAfterSeconds()));
      return ResponseEntity.status(ex.getHttpStatus()).headers(headers).body(body);
    }
    return ResponseEntity.status(ex.getHttpStatus()).body(body);
  }

  /**
   * Re-throws Spring Security exceptions ({@link AccessDeniedException}, {@link
   * AuthenticationException}) so they propagate through the security filter chain and reach the
   * registered {@code AccessDeniedHandler} / {@code AuthenticationEntryPoint}.
   *
   * <p>Without this, the catch-all {@link #handleUnexpected} would intercept 403/401 paths and
   * incorrectly return 500.
   */
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  public void reThrowSecurityException(RuntimeException ex) {
    throw ex;
  }

  /**
   * Catch-all for any unhandled {@link Exception}. Generates a unique {@code request_id}, logs the
   * full stack trace at ERROR level (never in the response body), and returns a generic 500.
   *
   * <p>Spring 6 MVC/Boot exceptions implement {@link ErrorResponse} (e.g. {@code
   * NoResourceFoundException} → 404, {@code MethodNotAllowedException} → 405). They are passed
   * through with their original status code and an empty body. Re-throwing them would cause an
   * infinite resolution loop inside {@code ExceptionHandlerExceptionResolver}; wrapping them in a
   * 500 envelope would be incorrect.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
    if (ex instanceof ErrorResponse er) {
      return ResponseEntity.status(er.getStatusCode()).build();
    }
    String requestId = UUID.randomUUID().toString();
    log.error("Unhandled exception [request_id={}]", requestId, ex);
    Map<String, Object> details = Map.of("request_id", requestId);
    ErrorEnvelope body =
        ErrorEnvelope.of(
            INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Contact support with the request_id.",
            details);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private static String defaultMessage(String message) {
    return message != null ? message : "invalid value";
  }
}
