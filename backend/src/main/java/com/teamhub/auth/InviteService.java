package com.teamhub.auth;

import com.teamhub.auth.dto.InviteResponse;
import com.teamhub.auth.dto.TokenResponse;
import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.UserInvited;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant invite flow (PRD §6.5, TASK-054).
 *
 * <p>An Admin invites a user by email + role. A 256-bit opaque token is generated; only its SHA-256
 * hash is persisted. The raw token is emailed directly and also published to the outbox so the
 * email-sender consumer can deliver it. The invitee accepts by presenting the raw token and setting
 * a password, which creates their user account and returns a live session.
 *
 * <p>Resend has a per-invite cooldown of 5 minutes enforced via the {@code resent_at} column.
 */
@Service
public class InviteService {

  /** Invite token TTL: 72 hours. */
  private static final long INVITE_TTL_SECONDS = 72L * 3600L;

  /** Resend cooldown: 5 minutes. */
  private static final long RESEND_COOLDOWN_SECONDS = 300L;

  private static final int TOKEN_BYTES = 32;

  private final InviteRepository inviteRepository;

  private final UserService userService;

  private final AuthService authService;

  private final OutboxPublisher outboxPublisher;

  private final JavaMailSender mailSender;

  private final String frontendUrl;

  private final QuotaEnforcementService quotaEnforcementService;

  private final SecureRandom secureRandom = new SecureRandom();

  public InviteService(
      InviteRepository inviteRepository,
      UserService userService,
      AuthService authService,
      OutboxPublisher outboxPublisher,
      JavaMailSender mailSender,
      @Value("${teamhub.frontend-url:http://localhost:5173}") String frontendUrl,
      QuotaEnforcementService quotaEnforcementService) {
    this.inviteRepository = inviteRepository;
    this.userService = userService;
    this.authService = authService;
    this.outboxPublisher = outboxPublisher;
    this.mailSender = mailSender;
    this.frontendUrl = frontendUrl;
    this.quotaEnforcementService = quotaEnforcementService;
  }

  /**
   * Creates a new invite for the given email + role within a tenant.
   *
   * <p>Throws {@link InviteAlreadyPendingException} when an active (non-accepted, non-expired)
   * invite already exists for this email + tenant.
   *
   * @param tenantId the tenant the invitee will join
   * @param actorUserId the Admin user creating the invite (for audit/event payload)
   * @param email invitee's email address
   * @param roleCode the role to assign (must map to a {@link UserRole} value)
   * @return response DTO for the persisted invite
   */
  @Transactional
  public InviteResponse createInvite(
      UUID tenantId, UUID actorUserId, String email, String roleCode) {
    quotaEnforcementService.checkSeatQuota(tenantId);

    Instant now = Instant.now();

    List<Invite> pending =
        inviteRepository.findByTenantIdAndEmailAndAcceptedAtIsNull(tenantId, email);
    boolean hasActive = pending.stream().anyMatch(i -> i.isActiveAt(now));
    if (hasActive) {
      throw new InviteAlreadyPendingException();
    }

    String rawToken = generateToken();
    String tokenHash = hash(rawToken);
    Instant expiresAt = now.plusSeconds(INVITE_TTL_SECONDS);

    Invite invite = new Invite(tenantId, email, roleCode, tokenHash, expiresAt);
    inviteRepository.save(invite);

    UserInvited payload =
        new UserInvited(
            UUID.randomUUID(), tenantId, email, actorUserId, roleCode, rawToken, expiresAt);
    outboxPublisher.publish("UserInvited", "Invite", invite.getId(), payload);

    sendInviteEmail(email, rawToken);

    return InviteResponse.from(invite);
  }

  /**
   * Accepts an invite: validates the token, creates the user account, and issues a session.
   *
   * @param rawToken the raw opaque invite token from the email link
   * @param password the new account password (min 8 chars)
   * @return a live access + refresh token pair (the invitee is logged in immediately)
   * @throws InviteUsedOrExpiredException when the token is unknown, expired, or already accepted
   */
  @Transactional
  public TokenResponse acceptInvite(String rawToken, String password) {
    String tokenHash = hash(rawToken);

    Invite invite =
        inviteRepository.findByTokenHash(tokenHash).orElseThrow(InviteUsedOrExpiredException::new);

    if (!invite.isActiveAt(Instant.now())) {
      throw new InviteUsedOrExpiredException();
    }

    UserRole role;
    try {
      role = UserRole.valueOf(invite.getRole());
    } catch (IllegalArgumentException e) {
      throw new InviteUsedOrExpiredException();
    }

    userService.createUser(
        invite.getTenantId(), invite.getEmail(), password, role, invite.getEmail());

    invite.setAcceptedAt(Instant.now());
    inviteRepository.save(invite);

    return TokenResponse.from(authService.login(invite.getEmail(), password));
  }

  /**
   * Accepts an invite via Google OAuth2: creates a password-less user account and marks the invite
   * consumed (PRD §6.1, TASK-021).
   *
   * <p>Unlike {@link #acceptInvite(String, String)}, no password is set — the user authenticates
   * exclusively through Google. The caller is responsible for issuing JWTs after this returns.
   *
   * @param invite the active, non-accepted invite to consume
   * @return the newly created {@link User}
   * @throws InviteUsedOrExpiredException if the invite role is invalid
   */
  @Transactional
  public User acceptInviteViaOAuth2(Invite invite) {
    UserRole role;
    try {
      role = UserRole.valueOf(invite.getRole());
    } catch (IllegalArgumentException e) {
      throw new InviteUsedOrExpiredException();
    }

    User user =
        userService.createOAuth2User(
            invite.getTenantId(), invite.getEmail(), role, invite.getEmail());

    invite.setAcceptedAt(Instant.now());
    inviteRepository.save(invite);

    return user;
  }

  /**
   * Resends an invite email with a freshly generated token.
   *
   * <p>Rate-limited to one resend per 5 minutes per invite.
   *
   * @param tenantId the owning tenant (for isolation)
   * @param inviteId the invite to resend
   * @throws InviteUsedOrExpiredException when the invite is not found or already accepted
   * @throws RateLimitExceededException when resend was called within the 5-minute cooldown
   */
  @Transactional
  public void resendInvite(UUID tenantId, UUID inviteId) {
    Invite invite =
        inviteRepository
            .findByIdAndTenantId(inviteId, tenantId)
            .orElseThrow(InviteUsedOrExpiredException::new);

    if (invite.getAcceptedAt() != null) {
      throw new InviteUsedOrExpiredException();
    }

    Instant now = Instant.now();
    if (invite.getResentAt() != null
        && now.getEpochSecond() - invite.getResentAt().getEpochSecond() < RESEND_COOLDOWN_SECONDS) {
      Instant windowResetAt = invite.getResentAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
      throw new RateLimitExceededException(windowResetAt);
    }

    String rawToken = generateToken();
    String tokenHash = hash(rawToken);
    Instant expiresAt = now.plusSeconds(INVITE_TTL_SECONDS);

    invite.setTokenHash(tokenHash);
    invite.setExpiresAt(expiresAt);
    invite.setResentAt(now);
    inviteRepository.save(invite);

    sendInviteEmail(invite.getEmail(), rawToken);
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private void sendInviteEmail(String to, String rawToken) {
    String inviteLink = frontendUrl + "/accept-invite?token=" + rawToken;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setSubject("You've been invited to Teamhub");
    // TO-DO: use a proper email template (PRD §6.5)
    message.setText(
        "You have been invited to join a team on Teamhub.\n\n"
            + "Click the link below to set your password and activate your account"
            + " (valid for 72 hours):\n\n"
            + inviteLink
            + "\n\nIf you did not expect this invitation, you can safely ignore this email.");

    mailSender.send(message);
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
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
}
