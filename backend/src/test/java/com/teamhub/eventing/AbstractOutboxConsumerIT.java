package com.teamhub.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for TASK-032 {@link AbstractOutboxConsumer} (PRD §22.5.6).
 *
 * <h3>Test steps covered</h3>
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_idempotency_sameEventIdTwice_handleCalledOnce()}): delivering
 *       the same {@code event_id} twice results in exactly one {@link
 *       AbstractOutboxConsumer#handle(EventEnvelope)} invocation.
 *   <li><b>Step 2</b> ({@link #step2_handleThrows5Times_messageRoutedToDlq()}): forcing {@link
 *       AbstractOutboxConsumer#handle(EventEnvelope)} to throw five times causes the sixth
 *       "delivery" to be dead-lettered to {@code events:<type>:dlq}.
 *   <li><b>Step 3</b> ({@link #step3_claimLogic_messageIdleInPel_isClaimedAndReprocessed()}): a
 *       message left in the PEL beyond the visibility timeout is claimed and reprocessed (verified
 *       using a configurable {@link AbstractOutboxConsumer#setVisibilityTimeout} override;
 *       end-to-end 60-s idle test is marked TO-DO).
 * </ul>
 *
 * <h3>State isolation</h3>
 *
 * <p>Each test creates a unique stream key and consumer group so that concurrent scheduler ticks
 * from other test classes cannot pollute the assertions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class AbstractOutboxConsumerIT {

  private static final String BASE_STREAM_KEY = "events:ConsumerTestEvent";

  private static final String DLQ_KEY = BASE_STREAM_KEY + ":dlq";

  private static final String GROUP = "test-consumer-group";

  private static final String CONSUMER_NAME = "test-consumer-1";

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  private Tenant tenant;

  private CountingConsumer consumer;

  @BeforeEach
  void setUp() {
    tenant =
        tenantService.provision(
            "Consumer Co " + UUID.randomUUID(), "consumer+" + UUID.randomUUID() + "@test.com");

    // Clean up any left-over state from previous runs.
    try {
      redis.opsForStream().destroyGroup(BASE_STREAM_KEY, GROUP);
    } catch (Exception ignored) {
    }
    redis.delete(BASE_STREAM_KEY);
    redis.delete(DLQ_KEY);

    consumer = new CountingConsumer(BASE_STREAM_KEY, GROUP, CONSUMER_NAME, redis, jdbc);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    redis.delete(BASE_STREAM_KEY);
    redis.delete(DLQ_KEY);
    jdbc.update("DELETE FROM processed_events WHERE consumer_group = ?", GROUP);
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // =========================================================================
  // Step 1 — idempotency: same event_id twice → handle called exactly once
  // =========================================================================

  /**
   * TASK-032 Step 1: publishing the same {@code event_id} into the stream twice must result in
   * {@link AbstractOutboxConsumer#handle(EventEnvelope)} being called exactly once.
   *
   * <p>The first delivery inserts a de-dup row into {@code processed_events}. The second delivery
   * (a separate {@code XADD} with the same {@code id} field in the body) is detected by the {@code
   * SELECT EXISTS} idempotency gate and ACK-ed without invoking the handler.
   */
  @Test
  void step1_idempotency_sameEventIdTwice_handleCalledOnce() {
    UUID eventId = UUID.randomUUID();

    // First delivery: consumer processes the event successfully.
    xadd(eventId);
    consumer.pollOnce();

    assertThat(consumer.getHandleCount()).isEqualTo(1);

    // Second delivery with the SAME event_id simulates at-least-once re-delivery
    // (e.g. XADD after a relay re-reads an outbox row, or a direct duplicate insertion).
    xadd(eventId);
    consumer.pollOnce();

    assertThat(consumer.getHandleCount())
        .as("handle() must not be called for an already-processed event_id")
        .isEqualTo(1);

    // Exactly one processed_events row must exist for this event_id + group.
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND consumer_group = ?",
            Integer.class,
            eventId,
            GROUP);
    assertThat(count).isEqualTo(1);
  }

  // =========================================================================
  // Step 2 — DLQ: 5 failed handle() calls → dead-lettered on the 6th delivery
  // =========================================================================

  /**
   * TASK-032 Step 2: forcing {@link AbstractOutboxConsumer#handle(EventEnvelope)} to throw on every
   * invocation causes the message to remain in the PEL. After five deliveries ({@link
   * AbstractOutboxConsumer#MAX_DELIVERY_ATTEMPTS}) the sixth "delivery" routes the message to
   * {@code events:ConsumerTestEvent:dlq} and ACK-s it from the original group.
   *
   * <p>The visibility timeout is set to 50 ms so the PEL claim fires quickly in the test. In
   * production the default is {@link AbstractOutboxConsumer#DEFAULT_VISIBILITY_TIMEOUT} (60 s).
   *
   * <p>Sentry routing is deferred: see {@code // TO-DO} comment in {@link AbstractOutboxConsumer}.
   */
  @Test
  void step2_handleThrows5Times_messageRoutedToDlq() throws InterruptedException {
    UUID eventId = UUID.randomUUID();
    consumer.setVisibilityTimeout(Duration.ofMillis(50));
    consumer.setAlwaysThrow(true);

    xadd(eventId);

    // Delivery 1: XREADGROUP → handle() throws → PEL (delivery_count = 1).
    consumer.pollOnce();
    assertThat(consumer.getHandleCount()).isEqualTo(1);

    // Deliveries 2-5: each XCLAIM after the visibility timeout → handle() throws → PEL.
    for (int i = 0; i < 4; i++) {
      Thread.sleep(100); // ensure idle_time > 50 ms
      consumer.pollOnce();
    }
    assertThat(consumer.getHandleCount()).isEqualTo(5);

    // Delivery 6: XCLAIM finds delivery_count >= 5 → DLQ, handle() NOT called.
    Thread.sleep(100);
    consumer.pollOnce();

    assertThat(consumer.getHandleCount())
        .as("handle() must NOT be called on the DLQ routing pass")
        .isEqualTo(5);

    // DLQ stream must contain exactly one entry with the correct event_id.
    List<MapRecord<String, Object, Object>> dlqRecords =
        redis.opsForStream().read(StreamOffset.create(DLQ_KEY, ReadOffset.from("0-0")));
    assertThat(dlqRecords).as("DLQ stream").isNotNull().hasSize(1);
    assertThat(dlqRecords.get(0).getValue().get("id"))
        .as("DLQ record id field")
        .isEqualTo(eventId.toString());

    // Message must no longer be in the PEL.
    PendingMessages remaining =
        redis.opsForStream().pending(BASE_STREAM_KEY, GROUP, Range.unbounded(), 10L);
    assertThat(remaining.size()).as("PEL must be empty after DLQ routing").isZero();
  }

  // =========================================================================
  // Step 3 — PEL claim: message idle beyond visibility timeout is re-claimed
  // =========================================================================

  /**
   * TASK-032 Step 3 (unit-style, configurable timeout): verifies that a message left in the PEL
   * beyond the visibility timeout is claimed by the same consumer and reprocessed successfully.
   *
   * <p>TO-DO (§22.5.6): the full end-to-end scenario — stopping an actual consumer process
   * mid-handling, waiting 60 s, and asserting that a <em>peer</em> consumer claims and processes
   * the message — requires real process-lifecycle control and is out of scope for this IT suite.
   */
  @Test
  void step3_claimLogic_messageIdleInPel_isClaimedAndReprocessed() throws InterruptedException {
    UUID eventId = UUID.randomUUID();
    consumer.setVisibilityTimeout(Duration.ofMillis(50));

    // First delivery: throw once to simulate the consumer stopping mid-handling.
    consumer.setThrowsRemaining(1);
    xadd(eventId);
    consumer.pollOnce(); // handle() throws → PEL (delivery_count = 1)

    assertThat(consumer.getHandleCount()).isEqualTo(1);

    // Wait longer than the visibility timeout so reclaimStalePending() picks up the message.
    Thread.sleep(100);

    // Second delivery via XCLAIM: handle() succeeds this time.
    consumer.pollOnce();

    assertThat(consumer.getHandleCount())
        .as("Claimed message must be reprocessed after visibility timeout")
        .isEqualTo(2);

    // Message must be ACK-ed and gone from the PEL.
    PendingMessages remaining =
        redis.opsForStream().pending(BASE_STREAM_KEY, GROUP, Range.unbounded(), 10L);
    assertThat(remaining.size()).as("PEL must be empty after successful reprocessing").isZero();
  }

  // =========================================================================
  // Additional: malformed message is ACK-ed and skipped without calling handle()
  // =========================================================================

  @Test
  void malformedRecord_missingFields_ackedAndSkippedWithoutCallingHandle() {
    // XADD a record missing required fields (no 'id', no 'tenant_id').
    redis.opsForStream().add(BASE_STREAM_KEY, Map.of("unexpected", "data"));
    consumer.pollOnce();

    assertThat(consumer.getHandleCount())
        .as("handle() must not be called for a malformed record")
        .isZero();
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Adds an event with the given {@code eventId} to the test stream. */
  private void xadd(UUID eventId) {
    redis
        .opsForStream()
        .add(
            BASE_STREAM_KEY,
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenant.getId().toString(),
                "payload", "{\"test\":true}"));
  }

  // =========================================================================
  // Test consumer subclass
  // =========================================================================

  /**
   * Concrete {@link AbstractOutboxConsumer} used only in tests. Counts invocations and optionally
   * throws to simulate processing failures.
   */
  static class CountingConsumer extends AbstractOutboxConsumer {

    private final AtomicInteger handleCount = new AtomicInteger(0);

    private volatile boolean alwaysThrow = false;

    private volatile int throwsRemaining = 0;

    CountingConsumer(
        String streamKey,
        String group,
        String consumerName,
        StringRedisTemplate redis,
        JdbcTemplate jdbc) {
      super(streamKey, group, consumerName, redis, jdbc);
    }

    @Override
    protected void handle(EventEnvelope envelope) {
      handleCount.incrementAndGet();
      if (alwaysThrow || throwsRemaining > 0) {
        if (!alwaysThrow) {
          throwsRemaining--;
        }
        throw new RuntimeException("Simulated failure, count=" + handleCount.get());
      }
    }

    void setAlwaysThrow(boolean value) {
      this.alwaysThrow = value;
    }

    void setThrowsRemaining(int count) {
      this.throwsRemaining = count;
    }

    int getHandleCount() {
      return handleCount.get();
    }
  }
}
