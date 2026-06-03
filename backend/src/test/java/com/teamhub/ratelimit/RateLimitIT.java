package com.teamhub.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.config.TestcontainersConfiguration;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the Redis-backed Bucket4j rate limiting (PRD §24.4, TASK-069).
 *
 * <ul>
 *   <li><b>Step 1</b>: Hit {@code POST /auth/login} 6 times from the same IP — 6th call returns 429
 *       with {@code Retry-After} header and {@code RATE_LIMIT_EXCEEDED} code.
 *   <li><b>Step 2</b>: Hit {@code POST /jobs/{id}/claim} 11 times as the same authenticated user —
 *       11th call returns 429.
 *   <li><b>Step 3</b>: Consume 3 auth tokens, then re-acquire the bucket from Redis directly via
 *       the {@link ProxyManager} bean and assert that the remaining token count is 2 (not 5),
 *       proving state is stored in Redis and would survive a process restart.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

  private static final String LOGIN_URL = "/api/v1/auth/login";

  private static final String CLAIM_URL_TEMPLATE = "/api/v1/jobs/%s/claim";

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private ProxyManager<String> rateLimitProxyManager;

  @Autowired private RateLimitProperties rateLimitProperties;

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void flushRateLimitKeys() {
    // Remove any leftover rate-limit keys so tests are isolated.
    var keys = redisTemplate.keys("ratelimit:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  // ---- Step 1: auth/login rate limit — 5 per minute per IP --------------------------------

  /**
   * Six consecutive calls to {@code /auth/login} from {@code 127.0.0.1} (MockMvc default
   * remoteAddr). The first 5 may return any status except 429; the 6th must return 429 with {@code
   * Retry-After} and error code {@code RATE_LIMIT_EXCEEDED}.
   */
  @Test
  void authLogin_sixthCallFromSameIp_returns429WithRetryAfter() throws Exception {
    String body = "{\"email\":\"x@x.com\",\"password\":\"wrong\"}";

    // Calls 1–5: consume all tokens.
    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
    }

    // Call 6: bucket exhausted → 429.
    mockMvc
        .perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
  }

  // ---- Step 2: job claim rate limit — 10 per minute per user ------------------------------

  /**
   * Eleven consecutive {@code POST /jobs/{id}/claim} calls as the same authenticated user. The
   * first 10 may return any status except 429; the 11th must return 429.
   */
  @Test
  void jobClaim_eleventhCallBySameUser_returns429() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = mintToken(userId, "TECHNICIAN");
    UUID jobId = UUID.randomUUID();
    String claimUrl = CLAIM_URL_TEMPLATE.formatted(jobId);

    // Calls 1–10: consume all tokens.
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              post(claimUrl)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
    }

    // Call 11: bucket exhausted → 429.
    mockMvc
        .perform(
            post(claimUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
  }

  // ---- Step 3: Redis persistence — remaining tokens survive re-acquisition ----------------

  /**
   * Consumes 3 auth tokens via MockMvc, then re-acquires the bucket from the {@link ProxyManager}
   * bean (backed by the same Redis Testcontainer) and asserts that the remaining count is 2.
   *
   * <p>This proves bucket state is stored in Redis: a process restart would start with the same
   * {@link ProxyManager} using the same Redis data and observe identical remaining tokens.
   */
  @Test
  void authBucket_afterThreeConsumptions_remainingTokensPersistedInRedis() throws Exception {
    String body = "{\"email\":\"persist@x.com\",\"password\":\"any\"}";

    // Consume 3 tokens (MockMvc remoteAddr = 127.0.0.1).
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Re-acquire the bucket directly via the ProxyManager using the same key.
    String key = "ratelimit:auth:ip:127.0.0.1";
    RateLimitProperties.RouteLimit routeLimit = rateLimitProperties.routes().get("auth-ip");
    BucketConfiguration config =
        BucketConfiguration.builder()
            .addLimit(
                io.github.bucket4j.Bandwidth.builder()
                    .capacity(routeLimit.limit())
                    .refillGreedy(
                        routeLimit.limit(),
                        java.time.Duration.ofSeconds(routeLimit.windowSeconds()))
                    .build())
            .build();

    var bucket = rateLimitProxyManager.builder().build(key, () -> config);
    long remaining = bucket.getAvailableTokens();

    // 5 initial tokens − 3 consumed = 2 remaining.
    assertThat(remaining).isEqualTo(2);
  }

  // ---- Helpers ----------------------------------------------------------------------------

  private String mintToken(UUID userId, String role) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", UUID.randomUUID().toString())
            .claim("role", role)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
