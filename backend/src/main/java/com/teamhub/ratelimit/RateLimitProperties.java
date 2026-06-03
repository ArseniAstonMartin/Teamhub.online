package com.teamhub.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-route rate-limit configuration (PRD §24.4, TASK-069).
 *
 * <p>Each entry in {@code routes} maps a logical route key to its token-bucket parameters. The keys
 * are referenced by name in {@link RateLimitFilter}:
 *
 * <ul>
 *   <li>{@code auth-ip} — 5 attempts per 60 s per IP on auth endpoints
 *   <li>{@code job-claim-user} — 10 claim calls per 60 s per authenticated user
 * </ul>
 *
 * @param routes map of logical route key → limit configuration
 */
@ConfigurationProperties(prefix = "teamhub.rate-limit")
public record RateLimitProperties(Map<String, RouteLimit> routes) {

  /**
   * Token-bucket parameters for a single route.
   *
   * @param limit maximum number of tokens (requests) in the window
   * @param windowSeconds rolling window length in seconds
   */
  public record RouteLimit(int limit, int windowSeconds) {}
}
