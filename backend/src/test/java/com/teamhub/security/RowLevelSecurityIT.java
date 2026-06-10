package com.teamhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies PostgreSQL Row-Level Security on the {@code users} table (PRD §7.2, TASK-018).
 *
 * <p>Tests connect as the {@code postgres} superuser (Testcontainers default). To test RLS, each
 * test uses {@code SET LOCAL ROLE teamhub_app} inside the Spring-managed transaction. The {@code
 * teamhub_app} role has no BYPASSRLS privilege, so the {@code tenant_isolation} policy is fully
 * enforced. The {@code @Transactional} annotation causes each test to roll back at the end, so all
 * state is isolated between tests.
 *
 * <p>{@code em.flush()} is called before role switch to force Hibernate to send pending INSERT SQL
 * to the database (as {@code postgres} superuser, bypassing RLS). JdbcTemplate shares the same
 * transaction-bound connection, so the flushed rows are visible to subsequent native SQL queries.
 */
@SpringBootTest
@IntegrationTest
@Transactional
class RowLevelSecurityIT {

  @Autowired private InternalTenantService tenantService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  @Test
  void rlsIsolatesUsersAcrossTenants() {
    // Arrange: provision two tenants as the postgres superuser (BYPASSRLS — RLS not applied).
    Tenant tenantA = tenantService.provision("Plumbing Co", "admin@plumbing.com");
    Tenant tenantB = tenantService.provision("Electrical Inc", "admin@electrical.com");

    // Flush pending Hibernate INSERTs to DB (as postgres, bypasses RLS) before role switch.
    em.flush();

    // Switch to the app role — from this point RLS is active on users.
    jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");

    // Act: set GUC to tenant A.
    jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantA.getId() + "'");
    List<Map<String, Object>> rowsA = jdbcTemplate.queryForList("SELECT email FROM users");

    // Assert: only tenant A's user is visible.
    // email is citext; JDBC may return a PGobject rather than String — compare via toString().
    assertThat(rowsA).hasSize(1);
    assertThat(rowsA.get(0).get("email").toString()).isEqualTo("admin@plumbing.com");

    // Act: switch GUC to tenant B.
    jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantB.getId() + "'");
    List<Map<String, Object>> rowsB = jdbcTemplate.queryForList("SELECT email FROM users");

    // Assert: only tenant B's user is visible.
    assertThat(rowsB).hasSize(1);
    assertThat(rowsB.get(0).get("email").toString()).isEqualTo("admin@electrical.com");
  }

  @Test
  void withoutGucSetQueriesReturnNoRows() {
    // Arrange: create a tenant with a user as superuser.
    tenantService.provision("Cleaning Pro", "admin@cleaning.com");

    // Flush pending INSERTs to DB (as postgres, bypasses RLS) before switching roles.
    em.flush();

    // Switch to app role WITHOUT setting the tenant GUC.
    jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");

    // Act: query users with no current_tenant_id GUC (RESET → empty string → NULLIF → NULL).
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM users");

    // Assert: deny-by-default — RLS policy evaluates to (tenant_id = NULL) which is always false.
    assertThat(rows).isEmpty();
  }

  // JWT-level forgery is covered by
  // AuthControllerIT#protectedEndpoint_withForgedSignature_returns401
  // (TASK-019): a token whose tenant_id is forged would require the RS256 private key to be signed,
  // and any token signed with a different key is rejected with 401 before reaching the data layer.
  // DB-level cross-tenant isolation is verified by rlsIsolatesUsersAcrossTenants above.
}
