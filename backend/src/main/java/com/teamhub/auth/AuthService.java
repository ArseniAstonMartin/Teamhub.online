package com.teamhub.auth;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import com.teamhub.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication flows: password login, refresh-token rotation, and logout (PRD §6.2).
 *
 * <p>These flows run without a tenant context bound to the request, so the login DB user (which is
 * not subject to RLS) performs the global email and refresh-token-hash lookups. The opaque refresh
 * token is a 256-bit random value returned to the client; only its SHA-256 hash is persisted.
 *
 * <p>Each successful or failed auth event emits an async audit row via {@link AsyncAuditService}
 * (PRD §19.2, TASK-028). The async write never blocks the auth response; failures are handled by
 * the configured {@code AsyncUncaughtExceptionHandler}.
 */
@Service
public class AuthService {

  /**
   * Sentinel tenant UUID used for {@code auth.login_failed} events when the attempted email is not
   * found in the {@code users} table (V9 migration inserts the corresponding sentinel row into
   * {@code tenants} so the FK constraint is satisfied). Full email is recorded per PRD §19.2 — no
   * PII redaction for auth failure events.
   */
  static final UUID SENTINEL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserRepository userRepository;

  private final RefreshTokenRepository refreshTokenRepository;

  private final PasswordEncoder passwordEncoder;

  private final JwtService jwtService;

  private final JwtProperties jwtProperties;

  private final AsyncAuditService asyncAuditService;

  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      AsyncAuditService asyncAuditService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.asyncAuditService = asyncAuditService;
  }

  /** A freshly issued access + refresh token pair handed back to the client. */
  public record IssuedTokens(
      String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}

  /**
   * Authenticates an email/password pair and issues a new token pair.
   *
   * <p>Convenience overload for callers without an IP address (e.g. service-layer tests). Delegates
   * to {@link #login(String, String, String)} with {@code sourceIp = null}.
   *
   * @throws InvalidCredentialsException if the email is unknown, the password is wrong, or the
   *     account is inactive
   */
  @Transactional
  public IssuedTokens login(String email, String rawPassword) {
    return login(email, rawPassword, null);
  }

  /**
   * Authenticates an email/password pair and issues a new token pair.
   *
   * <p>Emits an async {@code auth.login} audit row on success and an {@code auth.login_failed} row
   * (containing email + IP per PRD §19.2) on failure. The audit write never blocks the response.
   *
   * <p>For {@code login_failed} events the {@code tenant_id} is resolved from the user's row when
   * the email exists; if the email is completely unknown the {@link #SENTINEL_TENANT_ID} is used so
   * the FK constraint in {@code audit_log} is always satisfied (V9 migration).
   *
   * @param sourceIp caller's source IP address; may be {@code null}
   * @throws InvalidCredentialsException if the email is unknown, the password is wrong, or the
   *     account is inactive
   */
  @Transactional
  public IssuedTokens login(String email, String rawPassword, String sourceIp) {
    Optional<User> maybeUser = userRepository.findByEmail(email);

    if (maybeUser.isEmpty()) {
      // Unknown email: use sentinel tenant so the audit FK is satisfied.
      asyncAuditService.recordAsync(
          buildLoginFailedEntry(SENTINEL_TENANT_ID, null, email, sourceIp));
      throw new InvalidCredentialsException();
    }

    User user = maybeUser.get();

    if (!user.isActive()
        || user.getPasswordHash() == null
        || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      // Known user, wrong credentials or account inactive.
      asyncAuditService.recordAsync(
          buildLoginFailedEntry(user.getTenant().getId(), user.getId(), email, sourceIp));
      throw new InvalidCredentialsException();
    }

    IssuedTokens tokens = issueTokens(user);
    asyncAuditService.recordAsync(
        buildLoginSuccessEntry(user.getTenant().getId(), user.getId(), sourceIp));
    return tokens;
  }

  /**
   * Issues a new access + refresh token pair for an already-authenticated user.
   *
   * <p>Used by the Google OAuth2 success handler (PRD §6.1, TASK-021) to issue Teamhub JWTs after
   * the OAuth2 identity has been resolved and verified. The caller is responsible for emitting the
   * appropriate audit event.
   *
   * @param user the verified, active user to issue tokens for
   * @return a fresh access + refresh token pair
   */
  @Transactional
  public IssuedTokens issueTokensFor(User user) {
    return issueTokens(user);
  }

  /**
   * Rotates a refresh token: validates the presented token, revokes it, and issues a brand-new
   * access + refresh pair.
   *
   * <p>Emits an async {@code auth.token_refresh} audit row on success.
   *
   * @throws InvalidRefreshTokenException if the token is unknown, expired, or already revoked, or
   *     if the owning user has been deactivated
   */
  @Transactional
  public IssuedTokens refresh(String rawRefreshToken) {
    // Pessimistic row lock serialises concurrent refreshes of the same token so rotation stays
    // single-use under READ COMMITTED isolation (the second caller re-reads the revoked row).
    RefreshToken existing =
        refreshTokenRepository
            .findByHashForUpdate(hash(rawRefreshToken))
            .orElseThrow(InvalidRefreshTokenException::new);

    Instant now = Instant.now();
    if (!existing.isActiveAt(now)) {
      throw new InvalidRefreshTokenException();
    }

    User user =
        userRepository
            .findById(existing.getUserId())
            .orElseThrow(InvalidRefreshTokenException::new);

    // A deactivated user must be locked out immediately, not after the 30-day refresh TTL.
    // Rejecting here (rather than issuing) blocks new access tokens; the throw rolls back so the
    // row is left untouched for the audit trail.
    if (!user.isActive()) {
      throw new InvalidRefreshTokenException();
    }

    existing.setRevokedAt(now);

    IssuedTokens tokens = issueTokens(user);
    asyncAuditService.recordAsync(buildTokenRefreshEntry(existing.getTenantId(), user.getId()));
    return tokens;
  }

  /**
   * Revokes the presented refresh token. Idempotent: an unknown or already-revoked token is a no-op
   * so logout never leaks whether a token existed.
   *
   * <p>Emits an async {@code auth.logout} audit row when a live token is revoked.
   */
  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenRepository
        .findByHash(hash(rawRefreshToken))
        .ifPresent(
            token -> {
              if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                asyncAuditService.recordAsync(
                    buildLogoutEntry(token.getTenantId(), token.getUserId()));
              }
            });
  }

  // -------------------------------------------------------------------------
  // Audit entry builders
  // -------------------------------------------------------------------------

  private static AuditLog buildLoginSuccessEntry(UUID tenantId, UUID userId, String sourceIp) {
    return AuditLog.builder()
        .tenantId(tenantId)
        .actorUserId(userId)
        .action("auth.login")
        .resourceType("auth")
        .resourceId(userId)
        .ipAddress(sourceIp)
        .build();
  }

  /**
   * Builds a {@code auth.login_failed} entry. The full email and source IP are stored in {@code
   * after_state} per PRD §19.2 — no PII redaction for auth failure events.
   */
  private static AuditLog buildLoginFailedEntry(
      UUID tenantId, UUID userId, String email, String sourceIp) {
    String detailsJson =
        "{\"email\":\""
            + escapeJson(email)
            + "\",\"ip\":\""
            + escapeJson(sourceIp != null ? sourceIp : "unknown")
            + "\"}";
    return AuditLog.builder()
        .tenantId(tenantId)
        .actorUserId(userId)
        .action("auth.login_failed")
        .resourceType("auth")
        .afterState(detailsJson)
        .ipAddress(sourceIp)
        .build();
  }

  private static AuditLog buildTokenRefreshEntry(UUID tenantId, UUID userId) {
    return AuditLog.builder()
        .tenantId(tenantId)
        .actorUserId(userId)
        .action("auth.token_refresh")
        .resourceType("auth")
        .resourceId(userId)
        .build();
  }

  private static AuditLog buildLogoutEntry(UUID tenantId, UUID userId) {
    return AuditLog.builder()
        .tenantId(tenantId)
        .actorUserId(userId)
        .action("auth.logout")
        .resourceType("auth")
        .resourceId(userId)
        .build();
  }

  /** Minimal JSON escaping for email/IP values in the login_failed after_state. */
  private static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private IssuedTokens issueTokens(User user) {
    JwtService.AccessToken access = jwtService.issue(user);

    String rawRefreshToken = generateRefreshToken();
    RefreshToken refreshToken =
        new RefreshToken(
            user.getId(),
            user.getTenant().getId(),
            hash(rawRefreshToken),
            Instant.now().plus(jwtProperties.refreshTokenTtl()));
    refreshTokenRepository.save(refreshToken);

    return new IssuedTokens(access.value(), access.expiresAt(), rawRefreshToken);
  }

  private String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
