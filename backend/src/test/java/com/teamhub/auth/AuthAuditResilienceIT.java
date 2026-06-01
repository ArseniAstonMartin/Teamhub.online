package com.teamhub.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.audit.AuditLogService;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Resilience tests verifying that audit failures do NOT break the HTTP auth response (PRD §19.2,
 * TASK-028 Step 3).
 *
 * <p>Uses {@code @SpyBean} to intercept {@link AuditLogService#record} and make it throw a {@link
 * RuntimeException}. The async executor absorbs the exception via the configured {@code
 * AsyncUncaughtExceptionHandler}; the calling HTTP thread sees a correct 200/401 response.
 *
 * <p>Each test resets the spy in {@code @BeforeEach} to prevent stubbing from leaking between
 * tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthAuditResilienceIT {

  private static final String PASSWORD = "Resilience-T3st!";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserService userService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @SpyBean private AuditLogService auditLogService;

  private Tenant tenant;

  private User user;

  @BeforeEach
  void setUp() {
    // Reset spy so doThrow stubs from previous tests don't carry over.
    reset(auditLogService);

    tenant =
        tenantService.provision(
            "Resilience Co " + UUID.randomUUID(), "resilience+" + UUID.randomUUID() + "@test.com");
    user =
        userService.createUser(
            tenant.getId(),
            "res-owner+" + UUID.randomUUID() + "@test.com",
            PASSWORD,
            UserRole.ADMIN,
            "Res Owner");
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update(
        "DELETE FROM audit_log WHERE tenant_id = ?", AuthService.SENTINEL_TENANT_ID);
    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  /**
   * TASK-028 Step 3 (success path): When {@link AuditLogService#record} throws a {@link
   * RuntimeException} (simulating a DB hiccup), a successful login still returns HTTP 200.
   *
   * <p>The async executor's {@code AsyncUncaughtExceptionHandler} catches the exception and logs it
   * at ERROR level — it does not propagate to the auth response thread.
   */
  @Test
  void auditFailure_doesNotBreakSuccessfulLoginResponse() throws Exception {
    doThrow(new RuntimeException("simulated audit DB hiccup"))
        .when(auditLogService)
        .record(argThat(e -> "auth.login".equals(e.getAction())));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), PASSWORD))))
        .andExpect(status().isOk());

    // Brief wait so the async thread has time to invoke the (throwing) spy.
    Thread.sleep(300);

    // Verify AuditLogService.record was called with the auth.login entry (and threw).
    verify(auditLogService, atLeastOnce()).record(argThat(e -> "auth.login".equals(e.getAction())));
  }

  /**
   * TASK-028 Step 3 (failure path): When the audit service throws on a {@code login_failed} event,
   * the HTTP response is still 401 (not 500).
   */
  @Test
  void auditFailure_doesNotBreakFailedLoginResponse() throws Exception {
    doThrow(new RuntimeException("simulated audit DB hiccup"))
        .when(auditLogService)
        .record(argThat(e -> "auth.login_failed".equals(e.getAction())));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest(user.getEmail(), "wrong-password"))))
        .andExpect(status().isUnauthorized());

    Thread.sleep(300);

    verify(auditLogService, atLeastOnce())
        .record(argThat(e -> "auth.login_failed".equals(e.getAction())));
  }
}
