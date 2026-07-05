package com.teamhub.auth;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password-reset flow (PRD §6.3, TASK-020).
 *
 * <p>Rate limit is enforced in-process via a {@link ConcurrentHashMap} with per-email attempt
 * counts within a rolling one-hour window. This is acceptable for MVP; a Redis-backed solution
 * should be used in production when multiple replicas are deployed.
 */
@Service
public class PasswordResetService {

  /** Rolling window duration for rate limiting: 1 hour. */
  private static final long RATE_LIMIT_WINDOW_SECONDS = 3600L;

  /** Maximum allowed forgot-password attempts per email per rolling window. */
  private static final int MAX_ATTEMPTS = 5;

  /** Reset token TTL: 30 minutes. */
  private static final long TOKEN_TTL_SECONDS = 1800L;

  private static final int RESET_TOKEN_BYTES = 32;

  private final UserRepository userRepository;

  private final PasswordResetTokenRepository tokenRepository;

  private final RefreshTokenRepository refreshTokenRepository;

  private final PasswordEncoder passwordEncoder;

  private final JavaMailSender mailSender;

  private final AsyncAuditService asyncAuditService;

  private final String frontendUrl;

  private final String mailFrom;

  /** Per-email rate-limit state: maps email → [attemptCount, windowStartEpochSecond]. */
  private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

  private final SecureRandom secureRandom = new SecureRandom();

  public PasswordResetService(
      UserRepository userRepository,
      PasswordResetTokenRepository tokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JavaMailSender mailSender,
      AsyncAuditService asyncAuditService,
      @Value("${teamhub.frontend-url:http://localhost:5173}") String frontendUrl,
      @Value("${teamhub.mail.from}") String mailFrom) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.mailSender = mailSender;
    this.asyncAuditService = asyncAuditService;
    this.frontendUrl = frontendUrl;
    this.mailFrom = mailFrom;
  }

  /**
   * Initiates a password-reset flow for the given email.
   *
   * <p>If no user is found for the email the method returns silently — no information about whether
   * the address is registered is exposed to the caller (PRD §6.3).
   *
   * @throws RateLimitExceededException when the caller has exceeded 5 attempts in the rolling hour
   */
  @Transactional
  public void requestReset(String email) {
    checkRateLimit(email);

    User user = userRepository.findByEmail(email).orElse(null);

    if (user == null) {
      asyncAuditService.recordAsync(
          buildAuditEntry(
              AuthService.SENTINEL_TENANT_ID, null, "auth.password_reset_requested", null));
      return;
    }

    String rawToken = generateToken();
    String tokenHash = hash(rawToken);
    Instant expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);

    tokenRepository.save(new PasswordResetToken(user.getId(), tokenHash, expiresAt));

    sendResetEmail(email, rawToken);

    asyncAuditService.recordAsync(
        buildAuditEntry(
            user.getTenant().getId(), user.getId(), "auth.password_reset_requested", null));
  }

  /**
   * Completes a password-reset using a previously issued raw token.
   *
   * @throws ResetTokenUsedOrExpiredException when the token is unknown, expired, or already used
   */
  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    String tokenHash = hash(rawToken);

    PasswordResetToken token =
        tokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(ResetTokenUsedOrExpiredException::new);

    if (!token.isValidAt(Instant.now())) {
      throw new ResetTokenUsedOrExpiredException();
    }

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(ResetTokenUsedOrExpiredException::new);

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    token.setUsedAt(Instant.now());
    tokenRepository.save(token);

    revokeAllRefreshTokens(user.getId());

    asyncAuditService.recordAsync(
        buildAuditEntry(
            user.getTenant().getId(), user.getId(), "auth.password_reset_completed", null));
  }

  // -------------------------------------------------------------------------
  // Rate limiting
  // -------------------------------------------------------------------------

  /**
   * Checks the rolling-window rate limit for the given email. Atomically increments the attempt
   * counter, resetting the window when it has expired.
   *
   * @throws RateLimitExceededException if the limit is exceeded
   */
  void checkRateLimit(String email) {
    long now = Instant.now().getEpochSecond();
    long[] state =
        rateLimitMap.compute(
            email,
            (key, existing) -> {
              if (existing == null || now - existing[1] >= RATE_LIMIT_WINDOW_SECONDS) {
                return new long[] {1L, now};
              }
              existing[0]++;
              return existing;
            });

    if (state[0] > MAX_ATTEMPTS) {
      long windowResetEpoch = state[1] + RATE_LIMIT_WINDOW_SECONDS;
      throw new RateLimitExceededException(Instant.ofEpochSecond(windowResetEpoch));
    }
  }

  /** Exposed for testing: clears all rate-limit state. */
  void clearRateLimitState() {
    rateLimitMap.clear();
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private void sendResetEmail(String to, String rawToken) {
    String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(to);
    message.setSubject("Reset your Teamhub password");
    // TO-DO: use a proper email template
    message.setText(
        "You requested a password reset.\n\nClick the link below to set a new password"
            + " (valid for 30 minutes):\n\n"
            + resetLink
            + "\n\nIf you did not request this, you can safely ignore this email.");

    mailSender.send(message);
  }

  private void revokeAllRefreshTokens(UUID userId) {
    Instant now = Instant.now();
    refreshTokenRepository.findAll().stream()
        .filter(rt -> userId.equals(rt.getUserId()) && rt.getRevokedAt() == null)
        .forEach(rt -> rt.setRevokedAt(now));
  }

  private String generateToken() {
    byte[] bytes = new byte[RESET_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static AuditLog buildAuditEntry(
      UUID tenantId, UUID userId, String action, @SuppressWarnings("unused") Object afterState) {
    return AuditLog.builder()
        .tenantId(tenantId)
        .actorUserId(userId)
        .action(action)
        .resourceType("auth")
        .resourceId(userId)
        .build();
  }

  /** Exposed for testing: returns the current in-memory rate-limit state map. */
  Map<String, long[]> getRateLimitMap() {
    return rateLimitMap;
  }

  /** Visible for testing: counter value for email in current window, 0 if no entry. */
  long getCurrentAttemptCount(String email) {
    long[] state = rateLimitMap.get(email);
    if (state == null) {
      return 0;
    }
    long now = Instant.now().getEpochSecond();
    if (now - state[1] >= RATE_LIMIT_WINDOW_SECONDS) {
      return 0;
    }
    return state[0];
  }
}
