package com.teamhub.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.RateLimitExceededException;
import com.teamhub.security.ErrorEnvelope;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces per-route token-bucket rate limits using Bucket4j backed by Redis
 * (PRD §24.4, TASK-069).
 *
 * <p>Two routes are protected:
 *
 * <ul>
 *   <li><b>auth-ip</b>: {@code POST /api/v1/auth/login} and {@code POST /api/v1/auth/refresh} are
 *       rate-limited to 5 requests per minute per IP address. The IP is read from {@code
 *       X-Forwarded-For} (first entry) falling back to {@link HttpServletRequest#getRemoteAddr()}.
 *   <li><b>job-claim-user</b>: {@code POST /api/v1/jobs/{id}/claim} is rate-limited to 10 requests
 *       per minute per user. The user ID is extracted from the {@code sub} claim of the Bearer
 *       token in the {@code Authorization} header (payload-only decode, no cryptographic
 *       re-verification needed for rate-limit key derivation).
 * </ul>
 *
 * <p>When a bucket is exhausted the filter writes a 429 response directly (using the uniform error
 * envelope, PRD §23.4) and includes the {@code Retry-After} header required by PRD §24.4.
 *
 * <p>Bucket state is stored in Redis so limits survive application restarts.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String AUTH_IP_ROUTE = "auth-ip";

  private static final String JOB_CLAIM_ROUTE = "job-claim-user";

  private static final Pattern CLAIM_PATH_PATTERN = Pattern.compile("^/api/v1/jobs/[^/]+/claim$");

  private final ProxyManager<String> proxyManager;

  private final RateLimitProperties properties;

  private final ObjectMapper objectMapper;

  /** Cache of pre-built BucketConfiguration per route key (immutable once built). */
  private final Map<String, BucketConfiguration> configCache = new ConcurrentHashMap<>();

  public RateLimitFilter(
      ProxyManager<String> proxyManager,
      RateLimitProperties properties,
      ObjectMapper objectMapper) {
    this.proxyManager = proxyManager;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    if (isAuthPath(path)) {
      String ip = resolveClientIp(request);
      String key = "ratelimit:auth:ip:" + ip;
      if (!consumeToken(key, AUTH_IP_ROUTE, response)) {
        return;
      }
    } else if (isClaimPath(path)) {
      String userId = extractSubFromBearerToken(request);
      if (userId != null) {
        String key = "ratelimit:claim:user:" + userId;
        if (!consumeToken(key, JOB_CLAIM_ROUTE, response)) {
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Tries to consume one token from the bucket identified by {@code key} using the route's limit
   * configuration. Returns {@code true} if the token was consumed (request may proceed) or {@code
   * false} if the bucket was exhausted (429 response already written to {@code response}).
   */
  private boolean consumeToken(String key, String routeKey, HttpServletResponse response)
      throws IOException {

    BucketConfiguration config = configCache.computeIfAbsent(routeKey, this::buildConfig);
    var bucket = proxyManager.builder().build(key, () -> config);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (probe.isConsumed()) {
      return true;
    }

    long waitNanos = probe.getNanosToWaitForRefill();
    long retryAfterSeconds = (waitNanos / 1_000_000_000L) + 1;
    write429Response(response, retryAfterSeconds);
    return false;
  }

  /** Writes a 429 JSON response with {@code Retry-After} and the uniform error envelope. */
  private void write429Response(HttpServletResponse response, long retryAfterSeconds)
      throws IOException {
    Instant resetAt = Instant.now().plusSeconds(retryAfterSeconds);
    RateLimitExceededException ex = new RateLimitExceededException(resetAt);
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
    ErrorEnvelope body = ErrorEnvelope.of(RateLimitExceededException.CODE, ex.getMessage());
    objectMapper.writeValue(response.getWriter(), body);
  }

  /** Builds a {@link BucketConfiguration} from the {@link RateLimitProperties} for a route key. */
  private BucketConfiguration buildConfig(String routeKey) {
    RateLimitProperties.RouteLimit limit = properties.routes().get(routeKey);
    if (limit == null) {
      throw new IllegalStateException(
          "No rate-limit configuration found for route key: " + routeKey);
    }
    Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(limit.limit())
            .refillGreedy(limit.limit(), Duration.ofSeconds(limit.windowSeconds()))
            .build();
    return BucketConfiguration.builder().addLimit(bandwidth).build();
  }

  private static boolean isAuthPath(String path) {
    return "/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path);
  }

  private static boolean isClaimPath(String path) {
    return CLAIM_PATH_PATTERN.matcher(path).matches();
  }

  /**
   * Resolves the client IP from {@code X-Forwarded-For} (first entry) or falls back to {@link
   * HttpServletRequest#getRemoteAddr()}.
   */
  static String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).strip();
    }
    return request.getRemoteAddr();
  }

  /**
   * Extracts the {@code sub} claim from the JWT Bearer token without cryptographic verification.
   * The result is used only as a rate-limit bucket key — not for authorization — so an unsigned
   * decode is safe here (a forged sub can only affect the forger's own bucket).
   *
   * @return the subject string, or {@code null} if the header is absent or malformed
   */
  static String extractSubFromBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7);
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      return null;
    }
    try {
      byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
      String payload = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
      // Minimal JSON parse: find "sub":"<value>"
      int subIdx = payload.indexOf("\"sub\"");
      if (subIdx < 0) {
        return null;
      }
      int colonIdx = payload.indexOf(':', subIdx + 5);
      if (colonIdx < 0) {
        return null;
      }
      int quoteOpen = payload.indexOf('"', colonIdx + 1);
      if (quoteOpen < 0) {
        return null;
      }
      int quoteClose = payload.indexOf('"', quoteOpen + 1);
      if (quoteClose < 0) {
        return null;
      }
      return payload.substring(quoteOpen + 1, quoteClose);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
