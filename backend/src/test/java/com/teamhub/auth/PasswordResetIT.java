package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.dto.ForgotPasswordRequest;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.auth.dto.ResetPasswordRequest;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the password-reset flow (PRD §6.3, TASK-020).
 *
 * <p>Uses {@link MockBean} to stub {@link JavaMailSender} so the raw token can be extracted from
 * the "sent" message body without requiring a live SMTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
@TestMethodOrder(MethodOrderer.MethodName.class)
class PasswordResetIT {

  private static final String EMAIL = "reset-test@example.com";

  private static final String INITIAL_PASSWORD = "Initial-P@ss1";

  private static final String NEW_PASSWORD = "NewP@ssw0rd!";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserService userService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private PasswordResetTokenRepository tokenRepository;

  @Autowired private PasswordResetService passwordResetService;

  @MockBean private JavaMailSender mailSender;

  /** Captures the raw token from the email body sent by {@link PasswordResetService}. */
  private final AtomicReference<String> capturedToken = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    passwordResetService.clearRateLimitState();
    capturedToken.set(null);

    doAnswer(
            invocation -> {
              SimpleMailMessage msg = invocation.getArgument(0);
              String body = msg.getText();
              if (body != null) {
                // Extract token from "?token=<raw_token>" in the reset link
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

    Tenant tenant = tenantRepository.save(new Tenant("Reset Test Co"));
    userService.createUser(tenant.getId(), EMAIL, INITIAL_PASSWORD, UserRole.DISPATCHER, "Tester");
  }

  /**
   * Step 1 — POST /forgot-password returns 204 and a {@code password_reset_tokens} row is inserted
   * with a non-null hash and a future expiry.
   */
  @Test
  void step1_forgotPassword_returns204AndInsertsDbRow() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL))))
        .andExpect(status().isNoContent());

    assertThat(capturedToken.get()).isNotBlank();

    String expectedHash = PasswordResetService.hash(capturedToken.get());
    var token = tokenRepository.findByTokenHash(expectedHash);
    assertThat(token).isPresent();
    assertThat(token.get().getExpiresAt()).isInTheFuture();
    assertThat(token.get().getUsedAt()).isNull();
  }

  /**
   * Step 2 — Resetting with the captured token returns 204, and subsequent login with the new
   * password succeeds.
   */
  @Test
  void step2_resetPassword_thenLoginWithNewPasswordSucceeds() throws Exception {
    // Trigger forgot-password to capture the token.
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL))))
        .andExpect(status().isNoContent());

    assertThat(capturedToken.get()).isNotBlank();

    // Reset password with the captured token.
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordRequest(capturedToken.get(), NEW_PASSWORD))))
        .andExpect(status().isNoContent());

    // Logging in with the new password must succeed.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, NEW_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  /**
   * Step 3 — Submitting the same reset token a second time returns 400 with code {@code
   * RESET_TOKEN_USED_OR_EXPIRED}.
   */
  @Test
  void step3_reuseResetToken_returns400WithCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL))))
        .andExpect(status().isNoContent());

    assertThat(capturedToken.get()).isNotBlank();
    String rawToken = capturedToken.get();

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordRequest(rawToken, NEW_PASSWORD))))
        .andExpect(status().isNoContent());

    // Second attempt with the same (now used) token must fail.
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordRequest(rawToken, "AnotherPass1!"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(ResetTokenUsedOrExpiredException.CODE));
  }

  /**
   * Step 4 — Calling /forgot-password 6 times for the same email in quick succession: the 6th call
   * returns 429 with a {@code Retry-After} header present.
   */
  @Test
  void step4_rateLimitExceeded_returns429WithRetryAfterHeader() throws Exception {
    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/forgot-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL))))
          .andExpect(status().isNoContent());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(EMAIL))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value(RateLimitExceededException.CODE))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  /**
   * Unknown email — POST /forgot-password returns 204 without leaking whether the address is
   * registered.
   */
  @Test
  void forgotPassword_unknownEmail_returns204Silently() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ForgotPasswordRequest("nobody@example.com"))))
        .andExpect(status().isNoContent());
  }

  /** Invalid email format → 422. */
  @Test
  void forgotPassword_invalidEmail_returns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ForgotPasswordRequest("not-an-email"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  /** Invalid token → 400 RESET_TOKEN_USED_OR_EXPIRED. */
  @Test
  void resetPassword_unknownToken_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordRequest("completely-fake-token", NEW_PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(ResetTokenUsedOrExpiredException.CODE));
  }
}
