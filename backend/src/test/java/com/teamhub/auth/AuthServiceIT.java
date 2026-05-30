package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the password-login, refresh-rotation, and logout flows (PRD §6.2, TASK-019). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthServiceIT {

  private static final String PASSWORD = "S3cr3t-passw0rd!";

  @Autowired private AuthService authService;

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private JwtDecoder jwtDecoder;

  private Tenant tenant;

  private User user;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(new Tenant("Plumbing Co"));
    user =
        userService.createUser(
            tenant.getId(), "owner@plumbing.com", PASSWORD, UserRole.ADMIN, "Owner");
  }

  @Test
  void login_issuesAccessTokenWithRequiredClaims() {
    AuthService.IssuedTokens tokens = authService.login("owner@plumbing.com", PASSWORD);

    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();

    Jwt jwt = jwtDecoder.decode(tokens.accessToken());
    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenant.getId().toString());
    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(jwt.getHeaders().get("kid")).isNotNull();
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isNotNull();

    // exp is ~15 minutes after iat (PRD §6.2).
    long ttlSeconds = ChronoUnit.SECONDS.between(jwt.getIssuedAt(), jwt.getExpiresAt());
    assertThat(ttlSeconds).isEqualTo(900);
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    assertThatThrownBy(() -> authService.login("owner@plumbing.com", "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials() {
    assertThatThrownBy(() -> authService.login("nobody@plumbing.com", PASSWORD))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refresh_rotatesToken_andRevokesPrevious() {
    AuthService.IssuedTokens first = authService.login("owner@plumbing.com", PASSWORD);

    AuthService.IssuedTokens second = authService.refresh(first.refreshToken());

    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(second.accessToken()).isNotBlank();

    // The original refresh token is now revoked and cannot be reused.
    assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
        .isInstanceOf(InvalidRefreshTokenException.class);

    // The new refresh token still works.
    assertThat(authService.refresh(second.refreshToken())).isNotNull();
  }

  @Test
  void logout_revokesRefreshToken_subsequentRefreshFails() {
    AuthService.IssuedTokens tokens = authService.login("owner@plumbing.com", PASSWORD);

    authService.logout(tokens.refreshToken());

    assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void refresh_unknownToken_throwsInvalidRefreshToken() {
    assertThatThrownBy(() -> authService.refresh("not-a-real-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void logout_unknownToken_isNoOp() {
    // Idempotent: logging out a token that was never issued must not raise.
    authService.logout("never-issued");
  }

  @Test
  void login_inactiveUser_throwsInvalidCredentials() {
    user.setActive(false);
    userRepository.save(user);

    assertThatThrownBy(() -> authService.login("owner@plumbing.com", PASSWORD))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refresh_expiredTokenInDb_throwsInvalidRefreshToken() {
    // Insert a refresh token that already expired; refresh must reject it.
    String raw = "expired-raw-token";
    RefreshToken expired =
        new RefreshToken(
            user.getId(), tenant.getId(), sha256Hex(raw), Instant.now().minus(1, ChronoUnit.DAYS));
    refreshTokenRepository.save(expired);

    assertThatThrownBy(() -> authService.refresh(raw))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
