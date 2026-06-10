package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.eventing.payloads.SubscriptionUpdated;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code quota-checker} consumer (PRD §17.3, §22.5.7, TASK-065).
 *
 * <h3>Test steps covered</h3>
 *
 * <ul>
 *   <li><b>Step 1a</b> ({@link #step1a_jobSubmittedAt80pct_fires80ThresholdEvent()}): Tenant with
 *       Starter plan (200-job limit); period state pre-seeded to job_count=159 (79%); publish one
 *       {@code JobSubmitted} → count becomes 160 (80%) → {@code QuotaThresholdReached(80)} appears
 *       in {@code outbox_events}.
 *   <li><b>Step 1b</b> ({@link #step1b_jobSubmittedAt100pct_fires100ThresholdEvent()}): State
 *       seeded to job_count=199; publish one more → count hits 200 (100%) → {@code
 *       QuotaThresholdReached(100)} emitted.
 *   <li><b>Step 2</b> ({@link #step2_duplicateAbove80pct_noSecond80Event()}): After
 *       threshold80_fired=true, another {@code JobSubmitted} produces no second {@code
 *       QuotaThresholdReached(80)} row.
 *   <li><b>Step 3</b> ({@link #step3_subscriptionUpdated_resetsState_thenNew80EventFires()}):
 *       Publish {@code SubscriptionUpdated} → state row resets. Seed count to 79% again → publish
 *       {@code JobSubmitted} → new {@code QuotaThresholdReached(80)} fires.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuotaCheckerIT {

  @Autowired private OutboxPublisher publisher;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private TenantQuotaStateRepository quotaStateRepository;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("quotaCheckerConsumer") private QuotaCheckerConsumer quotaCheckerConsumer;

  @Autowired
  @Qualifier("subscriptionUpdatedConsumer") private SubscriptionUpdatedConsumer subscriptionUpdatedConsumer;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provision(
            "Quota Checker Co " + UUID.randomUUID(),
            "quota-checker+" + UUID.randomUUID() + "@test.com");
    tenant.setPlan(Plan.STARTER);
    tenantRepository.saveAndFlush(tenant);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(() -> redis.opsForValue().get("relay:leader") != null);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM tenant_quota_state WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    // Re-delete processed_events: background consumers (e.g. push-fan-out-technicians) may have
    // inserted rows between the first delete (line 102) and this tenant delete.
    jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // =========================================================================
  // Step 1a — Reach 80% → QuotaThresholdReached(80) emitted
  // =========================================================================

  /**
   * TASK-065 Step 1a: Starter tenant (200-job limit); seed job_count=159 (79.5%); publish one
   * {@code JobSubmitted}. Consumer must increment to 160 (80%) and emit {@code
   * QuotaThresholdReached(threshold_pct=80)} to the outbox.
   */
  @Test
  void step1a_jobSubmittedAt80pct_fires80ThresholdEvent() throws Exception {
    seedQuotaState(159, false, false);

    UUID jobId = UUID.randomUUID();
    publishJobSubmittedDirectly(jobId);

    quotaCheckerConsumer.pollOnce();

    TenantQuotaState state = quotaStateRepository.findById(tenant.getId()).orElseThrow();
    assertThat(state.getJobCount()).as("job_count must be incremented to 160").isEqualTo(160);
    assertThat(state.isThreshold80Fired()).as("threshold80_fired must be true").isTrue();
    assertThat(state.isThreshold100Fired()).as("threshold100_fired must remain false").isFalse();

    assertThat(countOutboxThresholdEvents(80))
        .as("one QuotaThresholdReached(80) event in outbox")
        .isEqualTo(1);
  }

  // =========================================================================
  // Step 1b — Reach 100% → QuotaThresholdReached(100) emitted
  // =========================================================================

  /**
   * TASK-065 Step 1b: Seed job_count=199 with threshold80 already fired; publish one {@code
   * JobSubmitted}. Consumer must increment to 200 (100%) and emit {@code
   * QuotaThresholdReached(threshold_pct=100)}.
   */
  @Test
  void step1b_jobSubmittedAt100pct_fires100ThresholdEvent() throws Exception {
    seedQuotaState(199, true, false);

    UUID jobId = UUID.randomUUID();
    publishJobSubmittedDirectly(jobId);

    quotaCheckerConsumer.pollOnce();

    TenantQuotaState state = quotaStateRepository.findById(tenant.getId()).orElseThrow();
    assertThat(state.getJobCount()).as("job_count must be incremented to 200").isEqualTo(200);
    assertThat(state.isThreshold100Fired()).as("threshold100_fired must be true").isTrue();

    assertThat(countOutboxThresholdEvents(100))
        .as("one QuotaThresholdReached(100) event in outbox")
        .isEqualTo(1);
  }

  // =========================================================================
  // Step 2 — No duplicate 80% event within the same period
  // =========================================================================

  /**
   * TASK-065 Step 2: After threshold80_fired=true, another {@code JobSubmitted} must NOT produce a
   * second {@code QuotaThresholdReached(80)} outbox row.
   */
  @Test
  void step2_duplicateAbove80pct_noSecond80Event() throws Exception {
    seedQuotaState(160, true, false);

    UUID jobId = UUID.randomUUID();
    publishJobSubmittedDirectly(jobId);

    quotaCheckerConsumer.pollOnce();

    assertThat(countOutboxThresholdEvents(80))
        .as("no second QuotaThresholdReached(80) event after threshold already fired")
        .isEqualTo(0);
  }

  // =========================================================================
  // Step 3 — Period rollover via SubscriptionUpdated re-arms thresholds
  // =========================================================================

  /**
   * TASK-065 Step 3: Publish a {@code SubscriptionUpdated} event → quota state resets (thresholds
   * re-armed). Then seed count to 79% and publish a {@code JobSubmitted} → a fresh {@code
   * QuotaThresholdReached(80)} fires.
   */
  @Test
  void step3_subscriptionUpdated_resetsState_thenNew80EventFires() throws Exception {
    seedQuotaState(200, true, true);

    publishSubscriptionUpdatedDirectly();
    subscriptionUpdatedConsumer.pollOnce();

    TenantQuotaState stateAfterReset = quotaStateRepository.findById(tenant.getId()).orElseThrow();
    assertThat(stateAfterReset.getJobCount()).as("job_count reset to 0").isEqualTo(0);
    assertThat(stateAfterReset.isThreshold80Fired())
        .as("threshold80_fired must be re-armed (false)")
        .isFalse();
    assertThat(stateAfterReset.isThreshold100Fired())
        .as("threshold100_fired must be re-armed (false)")
        .isFalse();

    // Now advance state to 79% and publish one more JobSubmitted → 80% threshold fires again.
    jdbc.update(
        "UPDATE tenant_quota_state SET job_count = 159 WHERE tenant_id = ?", tenant.getId());

    UUID jobId = UUID.randomUUID();
    publishJobSubmittedDirectly(jobId);

    quotaCheckerConsumer.pollOnce();

    assertThat(countOutboxThresholdEvents(80))
        .as("QuotaThresholdReached(80) must fire again after period reset")
        .isEqualTo(1);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Seeds a {@code tenant_quota_state} row using JDBC (period_start = start of current calendar
   * month so it matches what {@link QuotaCheckerService} computes).
   */
  private void seedQuotaState(int jobCount, boolean t80, boolean t100) {
    String periodStart = Instant.now().toString().substring(0, 7) + "-01T00:00:00Z";
    jdbc.update(
        "INSERT INTO tenant_quota_state"
            + " (tenant_id, period_start, job_count, threshold80_fired, threshold100_fired)"
            + " VALUES (?, ?::timestamptz, ?, ?, ?)"
            + " ON CONFLICT (tenant_id) DO UPDATE"
            + " SET period_start = EXCLUDED.period_start,"
            + "     job_count = EXCLUDED.job_count,"
            + "     threshold80_fired = EXCLUDED.threshold80_fired,"
            + "     threshold100_fired = EXCLUDED.threshold100_fired",
        tenant.getId(),
        periodStart,
        jobCount,
        t80,
        t100);
  }

  /** Adds a {@code JobSubmitted} message directly to {@code events:JobSubmitted} Redis stream. */
  private void publishJobSubmittedDirectly(UUID jobId) throws Exception {
    UUID eventId = UUID.randomUUID();
    String payloadJson =
        objectMapper.writeValueAsString(
            new JobSubmitted(eventId, tenant.getId(), jobId, null, "PLUMBING", null));
    redis
        .opsForStream()
        .add(
            "events:JobSubmitted",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenant.getId().toString(),
                "payload", payloadJson));
  }

  /**
   * Adds a {@code SubscriptionUpdated} message directly to {@code events:SubscriptionUpdated} Redis
   * stream.
   */
  private void publishSubscriptionUpdatedDirectly() throws Exception {
    UUID eventId = UUID.randomUUID();
    String payloadJson =
        objectMapper.writeValueAsString(
            new SubscriptionUpdated(
                eventId, tenant.getId(), "sub_test", "STARTER", "STARTER", Instant.now()));
    redis
        .opsForStream()
        .add(
            "events:SubscriptionUpdated",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenant.getId().toString(),
                "payload", payloadJson));
  }

  /** Counts {@code QuotaThresholdReached} outbox events for the tenant with the given pct. */
  private int countOutboxThresholdEvents(int thresholdPct) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ?"
                + "   AND event_type = 'QuotaThresholdReached'"
                + "   AND payload::jsonb ->> 'threshold_pct' = ?",
            Integer.class,
            tenant.getId(),
            String.valueOf(thresholdPct));
    return count != null ? count : 0;
  }
}
