package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.auth.dto.TokenResponse;
import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies TASK-131 fix: {@link InviteService#acceptInvite} sets password on placeholder users with
 * null {@code password_hash}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InviteAcceptPlaceholderUserUnitTest {

  @Mock private InviteRepository inviteRepository;

  @Mock private UserRepository userRepository;

  @Mock private UserService userService;

  @Mock private AuthService authService;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private OutboxPublisher outboxPublisher;

  @Mock private JavaMailSender mailSender;

  @Mock private QuotaEnforcementService quotaEnforcementService;

  @Test
  void acceptInvite_placeholderUser_setsPasswordHash() {
    UUID tenantId = UUID.randomUUID();
    String rawToken = "raw-token";
    String tokenHash = InviteService.hash(rawToken);

    Invite invite =
        new Invite(tenantId, "owner@acme.com", "ADMIN", tokenHash, Instant.now().plusSeconds(3600));
    invite.setId(UUID.randomUUID());

    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    User placeholder = new User(tenant, "owner@acme.com", UserRole.ADMIN, "owner@acme.com");
    placeholder.setId(UUID.randomUUID());

    when(inviteRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invite));
    when(userRepository.findByEmail("owner@acme.com")).thenReturn(Optional.of(placeholder));
    when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
    when(authService.login("owner@acme.com", "password123"))
        .thenReturn(
            new AuthService.IssuedTokens("access", Instant.now().plusSeconds(900), "refresh"));

    InviteService service =
        new InviteService(
            inviteRepository,
            userRepository,
            userService,
            authService,
            passwordEncoder,
            outboxPublisher,
            mailSender,
            "http://localhost:5173",
            "dev@teamhub.online",
            quotaEnforcementService);

    TokenResponse response = service.acceptInvite(rawToken, "password123");

    assertThat(response.accessToken()).isEqualTo("access");
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-hash");
    verify(userService, never()).createUser(any(), any(), any(), any(), any());
  }

  @Test
  void acceptInvite_noExistingUser_createsViaUserService() {
    UUID tenantId = UUID.randomUUID();
    String rawToken = "raw-token";
    String tokenHash = InviteService.hash(rawToken);

    Invite invite =
        new Invite(tenantId, "owner@acme.com", "ADMIN", tokenHash, Instant.now().plusSeconds(3600));

    when(inviteRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invite));
    when(userRepository.findByEmail("owner@acme.com")).thenReturn(Optional.empty());
    when(authService.login("owner@acme.com", "password123"))
        .thenReturn(
            new AuthService.IssuedTokens("access", Instant.now().plusSeconds(900), "refresh"));

    InviteService service =
        new InviteService(
            inviteRepository,
            userRepository,
            userService,
            authService,
            passwordEncoder,
            outboxPublisher,
            mailSender,
            "http://localhost:5173",
            "dev@teamhub.online",
            quotaEnforcementService);

    service.acceptInvite(rawToken, "password123");

    verify(userService)
        .createUser(
            eq(tenantId),
            eq("owner@acme.com"),
            eq("password123"),
            eq(UserRole.ADMIN),
            eq("owner@acme.com"));
  }
}
