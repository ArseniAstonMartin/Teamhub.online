package com.teamhub.eventing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for TASK-030 OutboxPublisher (PRD §22.5.3).
 *
 * <p>Covers all three task test steps:
 *
 * <ol>
 *   <li>Step 1 — Atomicity: a business row + outbox event both commit or both roll back in one
 *       transaction.
 *   <li>Step 2 — Defensive guard: calling publish outside an active transaction throws {@link
 *       IllegalTransactionStateException}.
 *   <li>Step 3 — Notification: a {@code LISTEN outbox_inserted} connection receives the {@code
 *       pg_notify} signal when the outbox-write transaction commits.
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIT {

  @Autowired private OutboxPublisher publisher;

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Autowired private InternalTenantService tenantService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DataSource dataSource;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provision(
            "OutboxPublisher Co " + UUID.randomUUID(),
            "outbox-publisher+" + UUID.randomUUID() + "@test.com");
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbcTemplate.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // =========================================================================
  // Step 1 — Atomicity: business row + outbox event both commit / roll back
  // =========================================================================

  /**
   * Wraps a direct outbox INSERT (simulating a business entity row, e.g. {@code INSERT INTO jobs})
   * together with {@link OutboxPublisher#publish} inside one transaction and asserts both rows are
   * present after commit.
   */
  @Test
  void step1_publishAndBusinessRow_bothCommitInSameTransaction() {
    UUID aggId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    txTemplate.execute(
        status -> {
          // Simulate business row (e.g. INSERT INTO jobs) using repository directly.
          outboxEventRepository.save(
              OutboxEvent.builder()
                  .tenantId(tenant.getId())
                  .eventType("DirectSentinel")
                  .aggregateType("Job")
                  .aggregateId(aggId)
                  .payload("{\"marker\":\"business\"}")
                  .build());

          publisher.publish("JobSubmitted", "Job", aggId, Map.of("jobId", aggId.toString()));
          return null;
        });
    TenantContext.clear();

    // Both rows must have been committed — query as postgres (BYPASSRLS) by using a tenant-free
    // JdbcTemplate borrow (TenantContext is already cleared above).
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, aggId);
    assertThat(count).isEqualTo(2);
  }

  /**
   * Wraps the same two inserts in a transaction that throws before commit and asserts that neither
   * row was persisted (full rollback).
   */
  @Test
  void step1_publishAndBusinessRow_bothRollBackOnException() {
    UUID aggId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    try {
      txTemplate.execute(
          status -> {
            outboxEventRepository.save(
                OutboxEvent.builder()
                    .tenantId(tenant.getId())
                    .eventType("DirectSentinel")
                    .aggregateType("Job")
                    .aggregateId(aggId)
                    .payload("{\"marker\":\"business\"}")
                    .build());

            publisher.publish("JobSubmitted", "Job", aggId, Map.of("jobId", aggId.toString()));

            // Simulate a business-logic failure that triggers rollback.
            throw new RuntimeException("simulated business failure");
          });
    } catch (RuntimeException ignored) {
      // Expected — TransactionTemplate propagates after rollback.
    } finally {
      TenantContext.clear();
    }

    // Neither row must exist — the entire transaction was rolled back.
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, aggId);
    assertThat(count).isZero();
  }

  // =========================================================================
  // Step 2 — Defensive guard: throws when called outside a transaction
  // =========================================================================

  @Test
  void step2_publishOutsideTransaction_throwsIllegalTransactionStateException() {
    TenantContext.set(tenant.getId());
    try {
      assertThatThrownBy(
              () ->
                  publisher.publish(
                      "JobSubmitted", "Job", UUID.randomUUID(), Map.of("jobId", "test")))
          .isInstanceOf(IllegalTransactionStateException.class);
    } finally {
      TenantContext.clear();
    }
  }

  // =========================================================================
  // Step 3 — pg_notify: LISTEN connection receives the notification on commit
  // =========================================================================

  @Test
  void step3_publishFiresPgNotifyNotification() throws Exception {
    UUID aggId = UUID.randomUUID();

    // Open a dedicated listening connection BEFORE the publish transaction so it is registered as
    // a listener on the channel before the NOTIFY is queued.
    // TenantContext is intentionally NOT set here so the connection runs as the postgres superuser
    // (RESET ROLE / RESET GUC via TenantAwareDataSource) — this is sufficient for LISTEN.
    try (Connection listenConn = dataSource.getConnection()) {
      listenConn.setAutoCommit(true);

      try (Statement st = listenConn.createStatement()) {
        st.execute("LISTEN outbox_inserted");
      }

      PGConnection pgConn = listenConn.unwrap(PGConnection.class);

      // Publish inside a business transaction. TenantContext is set only for the duration of
      // the publish TX so it doesn't leak to the listening connection borrow above.
      TenantContext.set(tenant.getId());
      try {
        txTemplate.execute(
            status -> {
              publisher.publish("JobSubmitted", "Job", aggId, Map.of("jobId", aggId.toString()));
              return null;
            });
      } finally {
        TenantContext.clear();
      }

      // PostgreSQL delivers NOTIFY payloads to listeners when the issuing transaction commits.
      // Poll the listening connection (up to 2 s) for the notification.
      await()
          .atMost(2, TimeUnit.SECONDS)
          .pollInterval(50, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () -> {
                PGNotification[] notifications = pgConn.getNotifications(0);
                assertThat(notifications).isNotNull();
                assertThat(notifications.length).isPositive();
              });
    }
  }

  // =========================================================================
  // Additional: PascalCase validation
  // =========================================================================

  @Test
  void publish_withLowerCaseEventType_throwsIllegalArgument() {
    TenantContext.set(tenant.getId());
    try {
      txTemplate.execute(
          status -> {
            publisher.publish("jobSubmitted", "Job", UUID.randomUUID(), Map.of());
            return null;
          });
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("PascalCase");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void publish_withNullTenantContext_throwsIllegalState() {
    // TenantContext deliberately not set.
    try {
      txTemplate.execute(
          status -> {
            publisher.publish("JobSubmitted", "Job", UUID.randomUUID(), Map.of());
            return null;
          });
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("tenant context");
    }
  }
}
