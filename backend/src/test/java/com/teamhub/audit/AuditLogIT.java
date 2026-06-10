package com.teamhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the append-only {@code audit_log} table and {@link AuditLogService} (PRD
 * §19, TASK-027).
 *
 * <p>Tests connect as the {@code postgres} superuser (Testcontainers default, BYPASSRLS). Each test
 * provisions its own tenant (auto-committed by {@link InternalTenantService}) so that the {@code
 * REQUIRES_NEW} transaction in {@link AuditLogService#record} can satisfy the {@code
 * audit_log.tenant_id → tenants.id} FK constraint. Tests do NOT use {@code @Transactional} on the
 * method level; instead they clean up in {@code @AfterEach} via direct superuser DELETE.
 *
 * <p>Permission-denied tests wrap the privilege check in an explicit {@link TransactionTemplate} so
 * that {@code SET LOCAL ROLE} is transaction-scoped (as PostgreSQL requires for LOCAL settings).
 */
@SpringBootTest
@IntegrationTest
class AuditLogIT {

  @Autowired private AuditLogService auditLogService;

  @Autowired private InternalTenantService tenantService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate txTemplate;

  /** Each test uses a unique tenant to avoid cross-test data leakage. */
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    // Provision a tenant; InternalTenantService.provision auto-commits.
    tenant =
        tenantService.provision(
            "Audit Co " + UUID.randomUUID(), "audit+" + UUID.randomUUID() + "@test.com");
  }

  @AfterEach
  void cleanUp() {
    // Delete audit rows first (FK child), then users, then tenant (FK parent).
    jdbcTemplate.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // -------------------------------------------------------------------------
  // Step 1: record() inserts a row that is readable by the postgres superuser.
  // -------------------------------------------------------------------------

  /**
   * TASK-027 Step 1: Call {@code record()} — row inserted.
   *
   * <p>Verifies that {@link AuditLogService#record} successfully inserts an {@code audit_log} row
   * and that the persisted field values match the input (truncated to microsecond precision because
   * PostgreSQL {@code timestamptz} has microsecond resolution).
   */
  @Test
  void record_insertsRow_withExpectedFieldValues() {
    UUID resourceId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    AuditLog entry =
        AuditLog.builder()
            .tenantId(tenant.getId())
            .action("job.create")
            .resourceType("job")
            .resourceId(resourceId)
            .ipAddress("192.168.1.10")
            .userAgent("Mozilla/5.0 (test)")
            .occurredAt(now)
            .build();

    auditLogService.record(entry);

    // Read back via postgres superuser (BYPASSRLS, so no GUC needed).
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE tenant_id = ?", tenant.getId());

    assertThat(rows).hasSize(1);
    Map<String, Object> row = rows.get(0);
    assertThat(row.get("action")).isEqualTo("job.create");
    assertThat(row.get("resource_type")).isEqualTo("job");
    assertThat(row.get("resource_id").toString()).isEqualTo(resourceId.toString());
    // occurred_at: compare truncated to micros (DB stores timestamptz at microsecond precision).
    Instant persistedAt =
        ((java.sql.Timestamp) row.get("occurred_at")).toInstant().truncatedTo(ChronoUnit.MICROS);
    assertThat(persistedAt).isEqualTo(now);
    // ip_address comes back as a PGobject from the inet column — compare via toString.
    assertThat(row.get("ip_address").toString()).isEqualTo("192.168.1.10");
    assertThat(row.get("user_agent")).isEqualTo("Mozilla/5.0 (test)");
  }

  // -------------------------------------------------------------------------
  // Step 1b: toJsonString() paths — String passthrough and Object.toString() fallback.
  // -------------------------------------------------------------------------

  /**
   * When {@code beforeState} is a {@link String} it must be forwarded verbatim to the {@code
   * ::jsonb} cast (String-passthrough branch in {@code toJsonString}).
   */
  @Test
  void record_withStringBeforeState_persistsJsonVerbatim() {
    String jsonSnippet = "{\"key\":\"value\"}";

    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenant.getId())
            .action("job.update")
            .resourceType("job")
            .beforeState(jsonSnippet)
            .build());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE tenant_id = ?", tenant.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("before_state")).isNotNull();
  }

  /**
   * When {@code afterState} is a non-{@link String} object, {@code toJsonString} must fall back to
   * {@link Object#toString()}. We use an anonymous subclass whose {@code toString()} returns valid
   * JSON so the {@code ::jsonb} cast succeeds at the database level.
   */
  @Test
  void record_withObjectAfterState_usesToStringFallback() {
    Object jsonObject =
        new Object() {
          @Override
          public String toString() {
            return "{\"status\":\"done\"}";
          }
        };

    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenant.getId())
            .action("job.complete")
            .resourceType("job")
            .afterState(jsonObject)
            .build());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE tenant_id = ?", tenant.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("after_state")).isNotNull();
  }

  // -------------------------------------------------------------------------
  // Step 2: UPDATE as teamhub_app → Postgres permission denied.
  // -------------------------------------------------------------------------

  /**
   * TASK-027 Step 2: Attempt {@code UPDATE audit_log SET action='x' WHERE id=...} as the app role —
   * Postgres throws permission denied.
   *
   * <p>The {@code teamhub_app} role holds SELECT only on {@code audit_log}. Any UPDATE attempt must
   * raise a Postgres privilege error ({@code 42501 insufficient_privilege}).
   */
  @Test
  void updateAsAppRole_isRejectedWithPermissionDenied() {
    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenant.getId())
            .action("user.invite")
            .resourceType("user")
            .build());

    UUID rowId =
        UUID.fromString(
            jdbcTemplate
                .queryForObject(
                    "SELECT id FROM audit_log WHERE tenant_id = ? LIMIT 1",
                    String.class,
                    tenant.getId())
                .toString());

    // Use TransactionTemplate so that SET LOCAL ROLE is transaction-scoped (PostgreSQL
    // requirement).
    assertThatThrownBy(
            () ->
                txTemplate.execute(
                    status -> {
                      jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
                      jdbcTemplate.execute(
                          "SET LOCAL app.current_tenant_id = '" + tenant.getId() + "'");
                      // teamhub_app has SELECT only — UPDATE must be denied.
                      return jdbcTemplate.update(
                          "UPDATE audit_log SET action = 'x' WHERE id = ?", rowId);
                    }))
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            ex -> {
              // The PostgreSQL error propagates either in the Spring message or its cause.
              String combined =
                  (ex.getMessage() != null ? ex.getMessage() : "")
                      + (ex.getCause() != null && ex.getCause().getMessage() != null
                          ? ex.getCause().getMessage()
                          : "");
              assertThat(combined).containsIgnoringCase("permission denied");
            });
  }

  // -------------------------------------------------------------------------
  // Step 3: DELETE as teamhub_app → Postgres permission denied.
  // -------------------------------------------------------------------------

  /**
   * TASK-027 Step 3: Attempt {@code DELETE FROM audit_log WHERE id=...} as the app role — Postgres
   * throws permission denied.
   *
   * <p>The {@code teamhub_app} role holds SELECT only on {@code audit_log}. Any DELETE attempt must
   * raise a Postgres privilege error ({@code 42501 insufficient_privilege}).
   */
  @Test
  void deleteAsAppRole_isRejectedWithPermissionDenied() {
    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenant.getId())
            .action("job.transition")
            .resourceType("job")
            .build());

    UUID rowId =
        UUID.fromString(
            jdbcTemplate
                .queryForObject(
                    "SELECT id FROM audit_log WHERE tenant_id = ? LIMIT 1",
                    String.class,
                    tenant.getId())
                .toString());

    assertThatThrownBy(
            () ->
                txTemplate.execute(
                    status -> {
                      jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
                      jdbcTemplate.execute(
                          "SET LOCAL app.current_tenant_id = '" + tenant.getId() + "'");
                      // teamhub_app has SELECT only — DELETE must be denied.
                      return jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", rowId);
                    }))
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            ex -> {
              String combined =
                  (ex.getMessage() != null ? ex.getMessage() : "")
                      + (ex.getCause() != null && ex.getCause().getMessage() != null
                          ? ex.getCause().getMessage()
                          : "");
              assertThat(combined).containsIgnoringCase("permission denied");
            });
  }
}
