package com.teamhub.eventing;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay: polls unprocessed {@link OutboxEvent} rows and forwards them to Redis Streams (PRD
 * §22.5.3).
 *
 * <h3>Leader election</h3>
 *
 * <p>Only one relay instance must be active at a time. Leadership is enforced via a Redis SET with
 * NX (create-if-absent) and a 10-second TTL: {@code SET relay:leader <instanceId> NX PX 10000}. The
 * leader refreshes the TTL every ~3 s using a Lua compare-and-refresh script that only extends the
 * TTL when the current lock value matches this instance's ID. On a clean shutdown, the key is
 * deleted so a peer can take over immediately.
 *
 * <h3>Relay loop</h3>
 *
 * <p>A 200 ms fixed-delay tick ({@link #relayTick()}) reads up to 100 unprocessed rows (ordered by
 * {@code created_at ASC}) from the outbox and {@code XADD}s each to {@code events:<eventType>}.
 * After a successful XADD the row is marked processed via a native UPDATE. If XADD fails after 5
 * retries, the failure is logged at ERROR level.
 *
 * <h3>RLS / multi-tenant access</h3>
 *
 * <p>The relay deliberately does <em>not</em> set {@link com.teamhub.shared.TenantContext}, so
 * {@link com.teamhub.shared.TenantAwareDataSource} issues {@code RESET ROLE} and {@code RESET
 * app.current_tenant_id} on each borrowed connection. The login user (postgres superuser /
 * BYPASSRLS) then sees all tenants' rows.
 *
 * <p>TO-DO: production deployments that use a non-superuser connection pool must either grant
 * BYPASSRLS to the relay role or use a dedicated relay schema-owner account.
 */
@Component
public class OutboxRelay implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  static final String LEADER_KEY = "relay:leader";

  private static final long LOCK_TTL_MS = 10_000L;

  private static final Duration LOCK_TTL = Duration.ofMillis(LOCK_TTL_MS);

  private static final int BATCH_SIZE = 100;

  private static final int MAX_XADD_ATTEMPTS = 5;

  /**
   * Lua script: refreshes the TTL on {@code KEYS[1]} only when its value matches {@code ARGV[1]}.
   * Returns 1 on success, 0 if the key is absent or owned by a different instance.
   */
  private static final DefaultRedisScript<Long> REFRESH_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then"
              + " return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end",
          Long.class);

  /**
   * Lua script: deletes {@code KEYS[1]} only when its value matches {@code ARGV[1]} (conditional
   * delete prevents removing a lock acquired by a different instance after a GC pause).
   */
  private static final DefaultRedisScript<Long> DELETE_IF_OWNER_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return"
              + " 0 end",
          Long.class);

  private final String instanceId = UUID.randomUUID().toString();

  private final AtomicBoolean leading = new AtomicBoolean(false);

  private final StringRedisTemplate redis;

  private final JdbcTemplate jdbc;

  private final OutboxEventRepository repository;

  public OutboxRelay(
      StringRedisTemplate redis, JdbcTemplate jdbc, OutboxEventRepository repository) {
    this.redis = redis;
    this.jdbc = jdbc;
    this.repository = repository;
  }

  // =========================================================================
  // Leader election heartbeat — runs every 3 s
  // =========================================================================

  /**
   * Acquires or refreshes relay leadership via a Redis lock.
   *
   * <ul>
   *   <li>If already leading: extend the TTL with a Lua compare-and-refresh. If the key has
   *       disappeared (crash/eviction), step down so the next tick can re-acquire.
   *   <li>If not leading: attempt {@code SET relay:leader <instanceId> NX PX 10000}.
   * </ul>
   */
  @Scheduled(fixedDelay = 3_000)
  public void electLeader() {
    if (leading.get()) {
      Long refreshed =
          redis.execute(
              REFRESH_SCRIPT, List.of(LEADER_KEY), instanceId, String.valueOf(LOCK_TTL_MS));
      if (!Long.valueOf(1L).equals(refreshed)) {
        leading.set(false);
        log.warn(
            "relay:leader TTL refresh failed — stepped down (instance={}); another instance may"
                + " have taken over",
            instanceId);
      }
    } else {
      Boolean acquired = redis.opsForValue().setIfAbsent(LEADER_KEY, instanceId, LOCK_TTL);
      if (Boolean.TRUE.equals(acquired)) {
        leading.set(true);
        log.info("relay:leader acquired (instance={})", instanceId);
      }
    }
  }

  // =========================================================================
  // Relay polling loop — runs every 200 ms
  // =========================================================================

  /**
   * Reads up to {@value #BATCH_SIZE} unprocessed outbox rows and forwards each to the correct Redis
   * Stream. Non-leaders return immediately without touching the database.
   */
  @Scheduled(fixedDelay = 200)
  public void relayTick() {
    if (!leading.get()) {
      return;
    }
    List<OutboxEvent> batch = repository.findUnprocessedBatch(BATCH_SIZE);
    for (OutboxEvent event : batch) {
      xaddWithRetry(event);
    }
  }

  // =========================================================================
  // XADD with retry
  // =========================================================================

  private void xaddWithRetry(OutboxEvent event) {
    String streamKey = "events:" + event.getEventType();
    Map<String, String> fields =
        Map.of(
            "id", event.getId().toString(),
            "tenant_id", event.getTenantId().toString(),
            "payload", event.getPayload());

    Exception lastException = null;
    for (int attempt = 1; attempt <= MAX_XADD_ATTEMPTS; attempt++) {
      try {
        redis.opsForStream().add(streamKey, fields);
        markProcessed(event.getId());
        return;
      } catch (Exception e) {
        lastException = e;
        log.warn(
            "XADD failed for event {} type={} (attempt {}/{}): {}",
            event.getId(),
            event.getEventType(),
            attempt,
            MAX_XADD_ATTEMPTS,
            e.getMessage());
      }
    }
    log.error(
        "XADD failed after {} retries for event {} type={} — event NOT forwarded to stream;"
            + " review Redis connectivity",
        MAX_XADD_ATTEMPTS,
        event.getId(),
        event.getEventType(),
        lastException);
    // TO-DO: route to Sentry once TASK-Sentry lands
  }

  private void markProcessed(UUID eventId) {
    jdbc.update("UPDATE outbox_events SET processed_at = now() WHERE id = ?", eventId);
  }

  // =========================================================================
  // Graceful shutdown
  // =========================================================================

  /**
   * Releases the Redis leader lock on shutdown. Uses a conditional Lua delete to avoid removing a
   * lock that was legitimately acquired by a peer after this instance's TTL lapsed.
   *
   * <p>Any exception (e.g. Redis connection already closed in a test context) is caught and logged
   * at WARN so the JVM shutdown sequence is not interrupted.
   */
  @Override
  public void destroy() {
    if (leading.get()) {
      try {
        redis.execute(DELETE_IF_OWNER_SCRIPT, List.of(LEADER_KEY), instanceId);
        log.info("relay:leader released on shutdown (instance={})", instanceId);
      } catch (Exception e) {
        log.warn(
            "relay:leader release failed on shutdown (instance={}) — key will expire via TTL: {}",
            instanceId,
            e.getMessage());
      }
    }
  }

  // =========================================================================
  // Package-visible accessors for tests
  // =========================================================================

  String getInstanceId() {
    return instanceId;
  }

  boolean isLeading() {
    return leading.get();
  }

  /**
   * Resets the in-process leading flag to {@code false}. Intended only for test setup to ensure a
   * clean leadership state before each test case.
   */
  void resetLeadingForTest() {
    leading.set(false);
  }
}
