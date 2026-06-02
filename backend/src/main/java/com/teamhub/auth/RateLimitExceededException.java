package com.teamhub.auth;

import com.teamhub.exception.DomainException;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a caller exceeds the forgot-password rate limit (5 attempts per email per rolling
 * hour). The HTTP response must include a {@code Retry-After} header whose value in seconds is
 * encoded in this exception so {@link com.teamhub.exception.GlobalExceptionHandler} can attach it
 * (PRD §6.3, TASK-020).
 */
public class RateLimitExceededException extends DomainException {

  public static final String CODE = "RATE_LIMIT_EXCEEDED";

  private final long retryAfterSeconds;

  /**
   * @param windowResetAt the instant at which the current rate-limit window expires
   */
  public RateLimitExceededException(Instant windowResetAt) {
    super(CODE, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later.");
    this.retryAfterSeconds =
        Math.max(0, windowResetAt.getEpochSecond() - Instant.now().getEpochSecond());
  }

  /** Seconds the caller must wait before the rate-limit window resets. */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
