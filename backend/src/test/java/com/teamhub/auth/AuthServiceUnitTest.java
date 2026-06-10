package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.security.JwtProperties;
import com.teamhub.tenancy.Tenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for {@link AuthService} last-login tracking (TASK-129). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AuthServiceUnitTest {

  private static final String EMAIL = "owner@plumbing.com";
  private static final String PASSWORD = "S3cr3t-passw0rd!";

  @Mock private UserRepository userRepository;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private JwtService jwtService;

  @Mock private AsyncAuditService asyncAuditService;

  private final JwtProperties jwtProperties =
      new JwtProperties(null, null, null, null, Duration.ofMinutes(15), Duration.ofDays(30));

  private AuthService service() {
    return new AuthService(
        userRepository,
        refreshTokenRepository,
        passwordEncoder,
        jwtService,
        jwtProperties,
        asyncAuditService);
  }

  @Test
  void login_success_setsLastLoginAt() {
    Tenant tenant = new Tenant("Plumbing Co");
    tenant.setId(UUID.randomUUID());
    User user = new User(tenant, EMAIL, UserRole.ADMIN, "Owner");
    user.setId(UUID.randomUUID());
    user.setPasswordHash("hashed");

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);
    when(jwtService.issue(user))
        .thenReturn(new JwtService.AccessToken("access-token", Instant.now().plusSeconds(900)));
    when(userRepository.save(user)).thenAnswer(inv -> inv.getArgument(0));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AuthService.IssuedTokens tokens = service().login(EMAIL, PASSWORD);

    assertThat(tokens.accessToken()).isEqualTo("access-token");
    assertThat(user.getLastLoginAt()).isNotNull();
    verify(userRepository).save(user);
  }

  @Test
  void issueTokensFor_setsLastLoginAt() {
    Tenant tenant = new Tenant("Plumbing Co");
    tenant.setId(UUID.randomUUID());
    User user = new User(tenant, EMAIL, UserRole.ADMIN, "Owner");
    user.setId(UUID.randomUUID());

    when(jwtService.issue(user))
        .thenReturn(new JwtService.AccessToken("access-token", Instant.now().plusSeconds(900)));
    when(userRepository.save(user)).thenAnswer(inv -> inv.getArgument(0));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service().issueTokensFor(user);

    assertThat(user.getLastLoginAt()).isNotNull();
    verify(userRepository).save(user);
  }
}
