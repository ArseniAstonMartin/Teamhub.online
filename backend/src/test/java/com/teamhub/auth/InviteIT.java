package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.dto.AcceptInviteRequest;
import com.teamhub.auth.dto.CreateInviteRequest;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.eventing.OutboxEventRepository;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the tenant invite flow (PRD §6.5, TASK-054).
 *
 * <p>Uses {@link MockBean} to stub {@link JavaMailSender} so the raw token can be extracted from
 * the "sent" message body without requiring a live SMTP server — same pattern as {@link
 * PasswordResetIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@TestMethodOrder(MethodOrderer.MethodName.class)
class InviteIT {

  private static final String INVITEE_EMAIL = "tech@example.com";

  private static final String ACCEPT_PASSWORD = "Secure-P@ss1";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserService userService;

  @Autowired private InviteRepository inviteRepository;

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @MockBean private JavaMailSender mailSender;

  /** Captures the raw token from the invite email body. */
  private final AtomicReference<String> capturedToken = new AtomicReference<>();

  private UUID tenantId;

  private String adminToken;

  @BeforeEach
  void setUp() {
    capturedToken.set(null);

    doAnswer(
            invocation -> {
              SimpleMailMessage msg = invocation.getArgument(0);
              String body = msg.getText();
              if (body != null) {
                int idx = body.indexOf("?token=");
                if (idx >= 0) {
                  String after = body.substring(idx + 7);
                  int end = after.indexOf('\n');
                  capturedToken.set(end >= 0 ? after.substring(0, end).trim() : after.trim());
                }
              }
              return null;
            })
        .when(mailSender)
        .send(any(SimpleMailMessage.class));

    Tenant tenant = tenantRepository.save(new Tenant("Test HVAC Co"));
    tenant.setPlan(Plan.STARTER);
    tenantRepository.save(tenant);
    tenantId = tenant.getId();
    userService.createUser(tenantId, "admin@example.com", "Admin-P@ss1", UserRole.ADMIN, "Admin");
    adminToken = mintToken(tenantId, UserRole.ADMIN.name());
  }

  /**
   * Step 1 — Admin POST /invites → 201, invite row in DB, outbox row with UserInvited event type.
   */
  @Test
  void step1_adminCreatesInvite_returns201AndPersistsRowAndOutboxEvent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.email").value(INVITEE_EMAIL))
        .andExpect(jsonPath("$.role").value("TECHNICIAN"))
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());

    assertThat(capturedToken.get()).isNotBlank();

    String expectedHash = InviteService.hash(capturedToken.get());
    var invite = inviteRepository.findByTokenHash(expectedHash);
    assertThat(invite).isPresent();
    assertThat(invite.get().getAcceptedAt()).isNull();
    assertThat(invite.get().getExpiresAt()).isInTheFuture();

    var outboxEvents = outboxEventRepository.findAll();
    assertThat(outboxEvents)
        .anyMatch(
            e -> "UserInvited".equals(e.getEventType()) && e.getPayload().contains(INVITEE_EMAIL));
  }

  /**
   * Step 2 — POST /invites/accept with valid token → 200 with JWTs. Login with new credentials
   * succeeds. User row has correct tenant_id and role.
   */
  @Test
  void step2_acceptInvite_returns200WithJwts_andUserIsCreated() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
        .andExpect(status().isCreated());

    assertThat(capturedToken.get()).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/invites/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AcceptInviteRequest(capturedToken.get(), ACCEPT_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest(INVITEE_EMAIL, ACCEPT_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    var userOpt =
        inviteRepository
            .findByTokenHash(InviteService.hash(capturedToken.get()))
            .map(i -> i.getAcceptedAt());
    assertThat(userOpt).isPresent();
  }

  /** Step 3 — Reuse the same token after acceptance → 400 INVITE_USED_OR_EXPIRED. */
  @Test
  void step3_reuseAcceptedToken_returns400WithCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
        .andExpect(status().isCreated());

    assertThat(capturedToken.get()).isNotBlank();
    String rawToken = capturedToken.get();

    mockMvc
        .perform(
            post("/api/v1/invites/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AcceptInviteRequest(rawToken, ACCEPT_PASSWORD))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/invites/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AcceptInviteRequest(rawToken, "AnotherPass1!"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(InviteUsedOrExpiredException.CODE));
  }

  /** Resend rate-limit: calling resend twice in < 5 minutes → second returns 429. */
  @Test
  void resend_twiceInCooldown_secondReturns429() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/invites")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
            .andExpect(status().isCreated())
            .andReturn();

    String inviteId =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/invites/" + inviteId + "/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/invites/" + inviteId + "/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value(RateLimitExceededException.CODE))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  /** Dispatcher attempting to create an invite → 403 FORBIDDEN. */
  @Test
  void createInvite_asDispatcher_returns403() throws Exception {
    String dispatcherToken = mintToken(tenantId, UserRole.DISPATCHER.name());

    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
        .andExpect(status().isForbidden());
  }

  /** Creating a second invite for the same email + tenant → 409 INVITE_ALREADY_PENDING. */
  @Test
  void createInvite_duplicatePending_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "TECHNICIAN"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/invites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest(INVITEE_EMAIL, "DISPATCHER"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value(InviteAlreadyPendingException.CODE));
  }

  /** Using a completely unknown token → 400 INVITE_USED_OR_EXPIRED. */
  @Test
  void acceptInvite_unknownToken_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invites/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AcceptInviteRequest("completely-fake-token", ACCEPT_PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(InviteUsedOrExpiredException.CODE));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private String mintToken(UUID tenantId, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
