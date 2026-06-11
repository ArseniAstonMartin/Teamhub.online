package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests verifying that each auth flow emits the correct {@code audit_log} row
 * asynchronously (PRD §19.2, TASK-028 Steps 1 and 2).
 *
 * <p>Tests do NOT use {@code @Transactional} at the class level because the async audit write
 * happens in a separate thread after the business transaction commits. All cleanup is done in
 * {@code @AfterEach} via direct superuser JDBC DELETE.
 */
@SpringBootTest
@IntegrationTest
class AuthAuditIT {

  private static final String PASSWORD = "Audit-T3st-Pass!";

  private static final String TEST_IP = "10.0.0.1";

  @Autowired private AuthService authService;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserService userService;

  @Autowired private JdbcTemplate jdbcTemplate;

  private Tenant tenant;

  private User user;

  @BeforeEach
  void setUp() {
    // InternalTenantService.provision auto-commits; the tenant row is visible to the async
    // audit thread when it fires its REQUIRES_NEW transaction.
    tenant =
        tenantService.provisionWithAdminUser(
            "Audit Test Co " + UUID.randomUUID(), "audituser+" + UUID.randomUUID() + "@test.com");
    user =
        userService.createUser(
            tenant.getId(),
            "audit-owner+" + UUID.randomUUID() + "@test.com",
            PASSWORD,
            UserRole.ADMIN,
            "Auditor");
  }

  @AfterEach
  void cleanUp() {
    // Delete in FK-dependency order: audit_log → refresh_tokens → users → tenants.
    jdbcTemplate.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update(
        "DELETE FROM audit_log WHERE tenant_id = ?", AuthService.SENTINEL_TENANT_ID);
    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // =========================================================================
  // TASK-028 Step 1: Successful login emits auth.login row within 1 s
  // =========================================================================

  /**
   * TASK-028 Step 1: A successful login must produce an {@code auth.login} row in {@code audit_log}
   * within 1 second (async write).
   */
  @Test
  void login_success_emitsAuthLoginAuditRow() {
    authService.login(user.getEmail(), PASSWORD, TEST_IP);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM audit_log WHERE tenant_id = ? AND action = 'auth.login'",
                      tenant.getId());
              assertThat(rows).hasSize(1);
              Map<String, Object> row = rows.get(0);
              assertThat(row.get("actor_user_id").toString()).isEqualTo(user.getId().toString());
              assertThat(row.get("resource_type")).isEqualTo("auth");
              assertThat(row.get("ip_address").toString()).isEqualTo(TEST_IP);
            });
  }

  // =========================================================================
  // TASK-028 Step 2: Failed login emits auth.login_failed with email + IP
  // =========================================================================

  /**
   * TASK-028 Step 2: A wrong-password attempt emits {@code auth.login_failed}; the {@code
   * after_state} JSON contains the attempted email and source IP per PRD §19.2.
   */
  @Test
  void login_wrongPassword_emitsLoginFailedAuditRowWithEmailAndIp() {
    assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong-password", TEST_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'auth.login_failed'",
                      tenant.getId());
              assertThat(rows).hasSize(1);
              Map<String, Object> row = rows.get(0);
              String afterState = row.get("after_state").toString();
              assertThat(afterState).contains(user.getEmail());
              assertThat(afterState).contains(TEST_IP);
              assertThat(row.get("ip_address").toString()).isEqualTo(TEST_IP);
            });
  }

  /**
   * Login_failed for a completely unknown email: falls back to the sentinel tenant UUID so the FK
   * constraint in {@code audit_log} is satisfied even when no matching user exists (V9 migration).
   */
  @Test
  void login_unknownEmail_emitsLoginFailedWithSentinelTenant() {
    String unknownEmail = "nobody+" + UUID.randomUUID() + "@nowhere.invalid";

    assertThatThrownBy(() -> authService.login(unknownEmail, "any", TEST_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'auth.login_failed'",
                      AuthService.SENTINEL_TENANT_ID);
              assertThat(rows).isNotEmpty();
              boolean found =
                  rows.stream()
                      .anyMatch(r -> r.get("after_state").toString().contains(unknownEmail));
              assertThat(found).isTrue();
            });
  }

  /** Token refresh emits an {@code auth.token_refresh} audit row. */
  @Test
  void refresh_emitsTokenRefreshAuditRow() {
    AuthService.IssuedTokens tokens = authService.login(user.getEmail(), PASSWORD, TEST_IP);

    // Remove the login row so the count is unambiguous.
    jdbcTemplate.update(
        "DELETE FROM audit_log WHERE tenant_id = ? AND action = 'auth.login'", tenant.getId());

    authService.refresh(tokens.refreshToken());

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'auth.token_refresh'",
                      tenant.getId());
              assertThat(rows).hasSize(1);
              assertThat(rows.get(0).get("actor_user_id").toString())
                  .isEqualTo(user.getId().toString());
            });
  }

  /** Logout emits an {@code auth.logout} audit row. */
  @Test
  void logout_emitsLogoutAuditRow() {
    AuthService.IssuedTokens tokens = authService.login(user.getEmail(), PASSWORD, TEST_IP);

    // Remove the login row.
    jdbcTemplate.update(
        "DELETE FROM audit_log WHERE tenant_id = ? AND action = 'auth.login'", tenant.getId());

    authService.logout(tokens.refreshToken());

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows =
                  jdbcTemplate.queryForList(
                      "SELECT * FROM audit_log" + " WHERE tenant_id = ? AND action = 'auth.logout'",
                      tenant.getId());
              assertThat(rows).hasSize(1);
              assertThat(rows.get(0).get("actor_user_id").toString())
                  .isEqualTo(user.getId().toString());
            });
  }
}
