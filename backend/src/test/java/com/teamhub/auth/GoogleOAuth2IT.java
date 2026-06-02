package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the Google OAuth2 success/failure handler logic (PRD §6.1, TASK-021).
 *
 * <p>The full OAuth2 redirect flow requires a real browser and a live Google account, so these
 * tests validate the handler directly by passing mocked {@link OAuth2AuthenticationToken} objects
 * with controlled email values. Real Spring beans and a Testcontainers PostgreSQL instance are used
 * for all service/repository calls.
 *
 * <p>Test steps:
 *
 * <ol>
 *   <li>Existing active user — handler issues Teamhub JWTs and redirects to success URL.
 *   <li>Deactivated user — handler redirects to failure URL with {@code error=ACCOUNT_DEACTIVATED}.
 *   <li>No user, no invite — handler redirects to failure URL with {@code
 *       error=NO_INVITE_FOR_EMAIL}.
 *   <li>Single pending invite — handler consumes invite, creates user, redirects to success URL.
 *   <li>Multiple pending invites (ambiguous tenant) — treated as no invite.
 *   <li>Failure handler — redirects to failure URL with {@code error=OAUTH2_ERROR}.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GoogleOAuth2IT {

  @Autowired private GoogleOAuth2SuccessHandler successHandler;

  @Autowired private GoogleOAuth2FailureHandler failureHandler;

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private InviteRepository inviteRepository;

  @Autowired private TenantRepository tenantRepository;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("OAuth2 Test Corp"));
    tenantId = tenant.getId();
  }

  /** Step 1 — Existing active user signs in via Google; handler redirects to success URL. */
  @Test
  void existingActiveUser_redirectsToSuccessUrlWithTokens() throws IOException, ServletException {
    userService.createUser(tenantId, "active@acme.com", "Password1!", UserRole.TECHNICIAN, "User");

    OAuth2AuthenticationToken token = buildOidcToken("active@acme.com");
    MockHttpServletResponse response = runSuccessHandler(token);

    assertThat(response.getRedirectedUrl())
        .contains("/auth/callback")
        .contains("access_token=")
        .contains("refresh_token=");
  }

  /** Step 2 — Deactivated user; handler redirects to failure URL with ACCOUNT_DEACTIVATED. */
  @Test
  void deactivatedUser_redirectsToFailureWithAccountDeactivated()
      throws IOException, ServletException {
    User user =
        userService.createUser(
            tenantId, "deact@acme.com", "Password1!", UserRole.TECHNICIAN, "Deactivated");
    user.setActive(false);
    userRepository.save(user);

    OAuth2AuthenticationToken token = buildOidcToken("deact@acme.com");
    MockHttpServletResponse response = runSuccessHandler(token);

    assertThat(response.getRedirectedUrl())
        .contains("/auth/error")
        .contains("error=ACCOUNT_DEACTIVATED");
  }

  /** Step 3 — No user, no invite; handler redirects to failure URL with NO_INVITE_FOR_EMAIL. */
  @Test
  void noUserNoInvite_redirectsToFailureWithNoInviteCode() throws IOException, ServletException {
    OAuth2AuthenticationToken token = buildOidcToken("noinvite@acme.com");
    MockHttpServletResponse response = runSuccessHandler(token);

    assertThat(response.getRedirectedUrl())
        .contains("/auth/error")
        .contains("error=NO_INVITE_FOR_EMAIL");
  }

  /**
   * Step 4 — Single pending invite; handler consumes invite, creates user, redirects to success.
   */
  @Test
  void singlePendingInvite_consumesInviteCreatesUserAndRedirectsWithTokens()
      throws IOException, ServletException {
    createInvite(tenantId, "invited@acme.com", "TECHNICIAN");

    OAuth2AuthenticationToken token = buildOidcToken("invited@acme.com");
    MockHttpServletResponse response = runSuccessHandler(token);

    assertThat(response.getRedirectedUrl())
        .contains("/auth/callback")
        .contains("access_token=")
        .contains("refresh_token=");

    assertThat(userRepository.findByEmail("invited@acme.com")).isPresent();

    assertThat(
            inviteRepository.findByEmailAndAcceptedAtIsNull("invited@acme.com").stream()
                .filter(i -> i.isActiveAt(Instant.now()))
                .toList())
        .isEmpty();
  }

  /**
   * Step 5 — Multiple active invites for the same email across tenants → ambiguous, treated as no
   * invite.
   */
  @Test
  void multiplePendingInvites_treatedAsNoInvite() throws IOException, ServletException {
    Tenant tenant2 = tenantRepository.save(new Tenant("Another Corp"));
    createInvite(tenantId, "multi@acme.com", "TECHNICIAN");
    createInvite(tenant2.getId(), "multi@acme.com", "DISPATCHER");

    OAuth2AuthenticationToken token = buildOidcToken("multi@acme.com");
    MockHttpServletResponse response = runSuccessHandler(token);

    assertThat(response.getRedirectedUrl())
        .contains("/auth/error")
        .contains("error=NO_INVITE_FOR_EMAIL");
  }

  /** Step 6 — Failure handler redirects to failure URL with OAUTH2_ERROR. */
  @Test
  void failureHandler_redirectsToFailureUrlWithOauth2Error() throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    failureHandler.onAuthenticationFailure(request, response, null);

    assertThat(response.getRedirectedUrl()).contains("/auth/error").contains("error=OAUTH2_ERROR");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private OAuth2AuthenticationToken buildOidcToken(String email) {
    OidcUser oidcUser = mock(OidcUser.class);
    when(oidcUser.getEmail()).thenReturn(email);
    OAuth2AuthenticationToken token = mock(OAuth2AuthenticationToken.class);
    when(token.getPrincipal()).thenReturn(oidcUser);
    return token;
  }

  private MockHttpServletResponse runSuccessHandler(OAuth2AuthenticationToken token)
      throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    successHandler.onAuthenticationSuccess(request, response, token);
    return response;
  }

  private void createInvite(UUID tid, String email, String role) {
    Invite invite =
        new Invite(
            tid, email, role, UUID.randomUUID().toString(), Instant.now().plusSeconds(72L * 3600L));
    inviteRepository.save(invite);
  }
}
