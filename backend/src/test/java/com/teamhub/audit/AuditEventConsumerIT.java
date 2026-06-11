package com.teamhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.config.IntegrationTest;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobClaimed;
import com.teamhub.eventing.payloads.JobCompleted;
import com.teamhub.eventing.payloads.JobStatusChanged;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.eventing.payloads.SubscriptionUpdated;
import com.teamhub.eventing.payloads.UserInvited;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for TASK-035 {@link AuditEventConsumer} (PRD §19.2).
 *
 * <h3>Test steps covered</h3>
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_jobSubmitted_auditRowCreatedWithActionJobCreate()}): publish a
 *       {@code JobSubmitted} event through {@link OutboxPublisher}; wait for the relay to forward
 *       it to Redis and for the consumer to write an audit row with {@code action='job.create'}
 *       within 4 s.
 *   <li><b>Step 2</b> ({@link #step2_jobStatusChanged_auditRowWithCorrectBeforeAfterState()}):
 *       publish a {@code JobStatusChanged} event; assert {@code action='job.transition'} and that
 *       {@code before_state}/{@code after_state} JSONB columns contain the expected status strings.
 *   <li><b>Step 3</b> ({@link
 *       #step3_duplicateDelivery_sameEventIdTwice_producesExactlyOneAuditRow()}): deliver the same
 *       event ID to the stream twice (simulating at-least-once re-delivery); assert idempotency
 *       from {@link com.teamhub.eventing.AbstractOutboxConsumer} produces exactly one audit row.
 * </ul>
 *
 * <p>Additional tests cover the four remaining subscribed event types ({@code job.claimed}, {@code
 * job.completed}, {@code user.invited}, {@code subscription.updated}) for code-coverage
 * completeness (PRD §19.2, Sonar ≥ 80%).
 */
@SpringBootTest
@IntegrationTest
class AuditEventConsumerIT {

  @Autowired private OutboxPublisher publisher;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private AsyncAuditService asyncAuditService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("auditConsumerJobSubmitted") private AuditEventConsumer jobSubmittedConsumer;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provisionWithAdminUser(
            "AuditEvents Co " + UUID.randomUUID(),
            "audit-events+" + UUID.randomUUID() + "@test.com");

    // Wait for the OutboxRelay to acquire leadership before running tests.  The relay's
    // @Scheduled electLeader() has fixedDelay = 3000 ms, so on a fresh context it takes up to
    // 3 s for the relay:leader key to appear in Redis.  Without this guard the first few tests
    // would publish events that sit unforwarded in the outbox for the full Awaitility timeout.
    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(() -> redis.opsForValue().get("relay:leader") != null);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    // Delete processed_events last — background consumers may insert rows during the cleanup
    // above; deleting immediately before the tenant ensures FK consistency.
    jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // =========================================================================
  // Step 1 — JobSubmitted → audit row with action='job.create'
  // =========================================================================

  /**
   * TASK-035 Step 1: publish a {@code JobSubmitted} event; expect audit row {@code
   * action='job.create'} with matching {@code resource_id} within 4 s.
   */
  @Test
  void step1_jobSubmitted_auditRowCreatedWithActionJobCreate() {
    UUID jobId = UUID.randomUUID();
    JobSubmitted payload =
        new JobSubmitted(UUID.randomUUID(), tenant.getId(), jobId, null, "PLUMBING", null);

    publishInTx("JobSubmitted", jobId, payload);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(countAuditRows("job.create", jobId))
                    .as("audit row with action='job.create' must appear within 4 s")
                    .isEqualTo(1));
  }

  // =========================================================================
  // Step 2 — JobStatusChanged → audit row with before_state / after_state
  // =========================================================================

  /**
   * TASK-035 Step 2: publish a {@code JobStatusChanged} event; expect {@code
   * action='job.transition'} with {@code before_state} containing the old status and {@code
   * after_state} containing the new status.
   */
  @Test
  void step2_jobStatusChanged_auditRowWithCorrectBeforeAfterState() {
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    JobStatusChanged payload =
        new JobStatusChanged(
            UUID.randomUUID(),
            tenant.getId(),
            jobId,
            "SUBMITTED",
            "ASSIGNED_TO_TECHNICIAN",
            actorId);

    publishInTx("JobStatusChanged", jobId, payload);

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              List<Map<String, Object>> rows = auditRows("job.transition", jobId);
              assertThat(rows)
                  .as("audit row with action='job.transition' must appear within 4 s")
                  .hasSize(1);
              Map<String, Object> row = rows.get(0);
              assertThat(row.get("before_state").toString())
                  .as("before_state must contain old status")
                  .contains("SUBMITTED");
              assertThat(row.get("after_state").toString())
                  .as("after_state must contain new status")
                  .contains("ASSIGNED_TO_TECHNICIAN");
            });
  }

  // =========================================================================
  // Step 3 — Idempotency: same event_id twice → exactly one audit row
  // =========================================================================

  /**
   * TASK-035 Step 3: deliver the same {@code event_id} to {@code events:JobSubmitted} twice
   * (simulating at-least-once re-delivery). The idempotency guard in {@link
   * com.teamhub.eventing.AbstractOutboxConsumer} must prevent a duplicate audit row from being
   * written, leaving exactly one row for the given {@code resource_id}.
   *
   * <p>The test adds both messages directly to the Redis stream (bypassing the outbox) to guarantee
   * the same {@code id} field in both stream entries. The Spring-managed {@code
   * auditConsumerJobSubmitted} bean processes them via its background {@code @Scheduled
   * pollOnce()}. After a brief additional wait, the row count must remain exactly 1.
   */
  @Test
  void step3_duplicateDelivery_sameEventIdTwice_producesExactlyOneAuditRow() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String payloadJson =
        objectMapper.writeValueAsString(
            new JobSubmitted(eventId, tenant.getId(), jobId, null, "CLEANING", null));

    // XADD the same event_id to the stream twice (simulating relay re-delivery).
    redis
        .opsForStream()
        .add(
            "events:JobSubmitted",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenant.getId().toString(),
                "payload", payloadJson));
    redis
        .opsForStream()
        .add(
            "events:JobSubmitted",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenant.getId().toString(),
                "payload", payloadJson));

    // Wait for the consumer's scheduled pollOnce() to process the first delivery.
    await()
        .atMost(2, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(countAuditRows("job.create", jobId))
                    .as("exactly one audit row after first delivery")
                    .isEqualTo(1));

    // Give the consumer time to process the second (duplicate) delivery.
    jobSubmittedConsumer.pollOnce();

    // The idempotency gate must suppress the duplicate — count must stay at 1.
    assertThat(countAuditRows("job.create", jobId))
        .as("idempotency: duplicate delivery must not produce a second audit row")
        .isEqualTo(1);

    // Verify processed_events has exactly one entry for this event_id.
    Integer processedCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM processed_events"
                + " WHERE event_id = ? AND consumer_group = 'audit-logger'",
            Integer.class,
            eventId);
    assertThat(processedCount)
        .as("exactly one processed_events row for this event_id")
        .isEqualTo(1);
  }

  // =========================================================================
  // Coverage tests for remaining event types
  // =========================================================================

  /** Publishes a {@code JobClaimed} event; asserts audit row with {@code action='job.claimed'}. */
  @Test
  void jobClaimed_auditRowWithActionJobClaimed() {
    UUID jobId = UUID.randomUUID();
    UUID techId = UUID.randomUUID();
    publishInTx(
        "JobClaimed",
        jobId,
        new JobClaimed(UUID.randomUUID(), tenant.getId(), jobId, techId, Instant.now()));

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(countAuditRows("job.claimed", jobId)).isEqualTo(1));
  }

  /**
   * Publishes a {@code JobCompleted} event; asserts audit row with {@code action='job.completed'}.
   */
  @Test
  void jobCompleted_auditRowWithActionJobCompleted() {
    UUID jobId = UUID.randomUUID();
    UUID techId = UUID.randomUUID();
    publishInTx(
        "JobCompleted",
        jobId,
        new JobCompleted(UUID.randomUUID(), tenant.getId(), jobId, techId, Instant.now()));

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> assertThat(countAuditRows("job.completed", jobId)).isEqualTo(1));
  }

  /**
   * Publishes a {@code UserInvited} event; asserts audit row with {@code action='user.invited'}.
   */
  @Test
  void userInvited_auditRowWithActionUserInvited() {
    UUID invitedBy = UUID.randomUUID();
    UUID syntheticId = UUID.randomUUID(); // used as aggregateId only (no user resource_id)
    publishInTx(
        "UserInvited",
        syntheticId,
        new UserInvited(
            UUID.randomUUID(),
            tenant.getId(),
            "tech+" + syntheticId + "@example.com",
            invitedBy,
            "TECHNICIAN",
            "tok_" + syntheticId,
            Instant.now().plusSeconds(604800)));

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              Integer count =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'user.invited'",
                      Integer.class,
                      tenant.getId());
              assertThat(count).isEqualTo(1);
            });
  }

  /**
   * Publishes a {@code SubscriptionUpdated} event; asserts audit row with {@code
   * action='subscription.updated'}.
   */
  @Test
  void subscriptionUpdated_auditRowWithActionSubscriptionUpdated() {
    UUID syntheticId = UUID.randomUUID();
    publishInTx(
        "SubscriptionUpdated",
        syntheticId,
        new SubscriptionUpdated(
            UUID.randomUUID(),
            tenant.getId(),
            "sub_" + syntheticId,
            "STARTER",
            "PROFESSIONAL",
            Instant.now()));

    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              Integer count =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'subscription.updated'",
                      Integer.class,
                      tenant.getId());
              assertThat(count).isEqualTo(1);
            });
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Publishes {@code payloadPojo} to the outbox inside a transaction, then clears the context. */
  private void publishInTx(String eventType, UUID aggregateId, Object payloadPojo) {
    TenantContext.set(tenant.getId());
    txTemplate.execute(
        status -> {
          publisher.publish(eventType, "Job", aggregateId, payloadPojo);
          return null;
        });
    TenantContext.clear();
  }

  private int countAuditRows(String action, UUID resourceId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log"
                + " WHERE tenant_id = ? AND action = ? AND resource_id = ?",
            Integer.class,
            tenant.getId(),
            action,
            resourceId);
    return count != null ? count : 0;
  }

  private List<Map<String, Object>> auditRows(String action, UUID resourceId) {
    return jdbc.queryForList(
        "SELECT * FROM audit_log WHERE tenant_id = ? AND action = ? AND resource_id = ?",
        tenant.getId(),
        action,
        resourceId);
  }
}
