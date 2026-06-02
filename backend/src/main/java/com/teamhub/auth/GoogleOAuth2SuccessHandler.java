package com.teamhub.auth;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Handles a successful Google OAuth2 login by resolving the Teamhub identity and issuing JWTs (PRD
 * §6.1, TASK-021).
 *
 * <p>Four outcomes are possible:
 *
 * <ol>
 *   <li><b>Existing active user</b> — issues Teamhub JWTs and redirects to the SPA callback URL.
 *   <li><b>Existing deactivated user</b> — redirects to the failure URL with {@code
 *       error=ACCOUNT_DEACTIVATED}.
 *   <li><b>No user, exactly one pending invite</b> — consumes the invite (creates a password-less
 *       account), issues JWTs, and redirects to the SPA callback URL.
 *   <li><b>No user, no invite (or multiple invites)</b> — redirects to the failure URL with {@code
 *       error=NO_INVITE_FOR_EMAIL}.
 * </ol>
 *
 * <p>Tenant resolution for the invite lookup: because there is no tenant context during the OAuth2
 * callback, invites are queried across all tenants by email. If exactly one active invite is found
 * its tenant is used; ambiguous or empty results are treated as "no invite".
 */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;

  private final InviteRepository inviteRepository;

  private final InviteService inviteService;

  private final AuthService authService;

  private final AsyncAuditService asyncAuditService;

  private final String successRedirectUrl;

  private final String failureRedirectUrl;

  public GoogleOAuth2SuccessHandler(
      UserRepository userRepository,
      InviteRepository inviteRepository,
      InviteService inviteService,
      AuthService authService,
      AsyncAuditService asyncAuditService,
      @Value("${teamhub.oauth2.success-redirect-url}") String successRedirectUrl,
      @Value("${teamhub.oauth2.failure-redirect-url}") String failureRedirectUrl) {
    this.userRepository = userRepository;
    this.inviteRepository = inviteRepository;
    this.inviteService = inviteService;
    this.authService = authService;
    this.asyncAuditService = asyncAuditService;
    this.successRedirectUrl = successRedirectUrl;
    this.failureRedirectUrl = failureRedirectUrl;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    String email = extractEmail(authentication);
    Optional<User> maybeUser = userRepository.findByEmail(email);

    if (maybeUser.isPresent()) {
      User user = maybeUser.get();
      if (!user.isActive()) {
        response.sendRedirect(failureRedirectUrl + "?error=ACCOUNT_DEACTIVATED");
        return;
      }
      AuthService.IssuedTokens tokens = authService.issueTokensFor(user);
      emitOAuth2LoginAudit(user.getTenant().getId(), user.getId());
      response.sendRedirect(buildSuccessUrl(tokens));
      return;
    }

    // No existing user — look for a pending invite across all tenants.
    List<Invite> pending = inviteRepository.findByEmailAndAcceptedAtIsNull(email);
    Instant now = Instant.now();
    List<Invite> active = pending.stream().filter(i -> i.isActiveAt(now)).toList();

    if (active.size() == 1) {
      Invite invite = active.get(0);
      User createdUser = inviteService.acceptInviteViaOAuth2(invite);
      AuthService.IssuedTokens tokens = authService.issueTokensFor(createdUser);
      emitOAuth2LoginAudit(createdUser.getTenant().getId(), createdUser.getId());
      response.sendRedirect(buildSuccessUrl(tokens));
      return;
    }

    response.sendRedirect(failureRedirectUrl + "?error=NO_INVITE_FOR_EMAIL");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private String extractEmail(Authentication authentication) {
    Object principal = ((OAuth2AuthenticationToken) authentication).getPrincipal();
    if (principal instanceof OidcUser oidcUser) {
      return oidcUser.getEmail();
    }
    if (principal instanceof OAuth2User oauth2User) {
      return oauth2User.getAttribute("email");
    }
    throw new IllegalStateException(
        "Cannot extract email from OAuth2 principal type: " + principal.getClass().getName());
  }

  private String buildSuccessUrl(AuthService.IssuedTokens tokens) {
    return successRedirectUrl
        + "?access_token="
        + tokens.accessToken()
        + "&refresh_token="
        + tokens.refreshToken();
  }

  private void emitOAuth2LoginAudit(UUID tenantId, UUID userId) {
    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(userId)
            .action("auth.oauth2.login")
            .resourceType("auth")
            .resourceId(userId)
            .build());
  }
}
