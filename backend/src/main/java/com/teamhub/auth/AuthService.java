package com.teamhub.auth;

import com.teamhub.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication flows: password login, refresh-token rotation, and logout (PRD §6.2).
 *
 * <p>These flows run without a tenant context bound to the request, so the login DB user (which is
 * not subject to RLS) performs the global email and refresh-token-hash lookups. The opaque refresh
 * token is a 256-bit random value returned to the client; only its SHA-256 hash is persisted.
 */
@Service
public class AuthService {

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserRepository userRepository;

  private final RefreshTokenRepository refreshTokenRepository;

  private final PasswordEncoder passwordEncoder;

  private final JwtService jwtService;

  private final JwtProperties jwtProperties;

  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
  }

  /** A freshly issued access + refresh token pair handed back to the client. */
  public record IssuedTokens(
      String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}

  /**
   * Authenticates an email/password pair and issues a new token pair.
   *
   * @throws InvalidCredentialsException if the email is unknown, the password is wrong, or the
   *     account is inactive
   */
  @Transactional
  public IssuedTokens login(String email, String rawPassword) {
    User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

    if (!user.isActive()
        || user.getPasswordHash() == null
        || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    return issueTokens(user);
  }

  /**
   * Rotates a refresh token: validates the presented token, revokes it, and issues a brand-new
   * access + refresh pair.
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

    return issueTokens(user);
  }

  /**
   * Revokes the presented refresh token. Idempotent: an unknown or already-revoked token is a no-op
   * so logout never leaks whether a token existed.
   */
  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenRepository
        .findByHash(hash(rawRefreshToken))
        .ifPresent(
            token -> {
              if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
              }
            });
  }

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
