package com.teamhub.eventing;

import com.teamhub.shared.TenantContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Base class for all Redis Streams event consumers (PRD §22.5.6).
 *
 * <p>Subclasses supply the stream key, consumer group, and consumer name via the constructor, and
 * implement the {@link #handle(EventEnvelope)} callback. The base class wraps every delivery in:
 *
 * <ol>
 *   <li><b>Idempotency guard</b> — {@code SELECT EXISTS} against {@code processed_events} before
 *       invoking the handler; if the event has already been processed the message is ACK-ed and
 *       skipped without calling {@link #handle(EventEnvelope)}.
 *   <li><b>Resilience</b> — on exception the message is left in the Pending Entries List (PEL) for
 *       retry. After {@value #MAX_DELIVERY_ATTEMPTS} failed deliveries the message is dead-lettered
 *       to {@code events:<type>:dlq} and ACK-ed.
 *   <li><b>Visibility timeout</b> — every tick, messages in the group's PEL that have been idle for
 *       longer than {@link #visibilityTimeout} (default 60 s) are re-claimed by this consumer for
 *       reprocessing ({@code XPENDING} + {@code XCLAIM}, PRD §22.5.6).
 * </ol>
 *
 * <h3>Idempotency note</h3>
 *
 * <p>The {@code processed_events} table is partitioned by month on {@code processed_at}. Because
 * PostgreSQL declarative partitioning requires the partition key in every unique constraint, the
 * physical PRIMARY KEY is {@code (event_id, consumer_group, processed_at)}. A bare {@code ON
 * CONFLICT DO NOTHING} on the PK alone is insufficient to detect re-deliveries that occur seconds
 * or minutes after the original INSERT (the timestamps would differ). This implementation therefore
 * uses {@code SELECT EXISTS} as the authoritative de-dup gate before invoking the handler, and
 * writes the de-dup record only after {@link #handle(EventEnvelope)} returns successfully.
 */
public abstract class AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(AbstractOutboxConsumer.class);

  static final int MAX_DELIVERY_ATTEMPTS = 5;

  static final Duration DEFAULT_VISIBILITY_TIMEOUT = Duration.ofSeconds(60);

  private final String streamKey;

  private final String consumerGroup;

  private final String consumerName;

  private final StringRedisTemplate redis;

  private final JdbcTemplate jdbc;

  /** Overrideable in tests to avoid sleeping 60 s to trigger a PEL claim. */
  private volatile Duration visibilityTimeout = DEFAULT_VISIBILITY_TIMEOUT;

  private final AtomicBoolean groupInitialized = new AtomicBoolean(false);

  protected AbstractOutboxConsumer(
      String streamKey,
      String consumerGroup,
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc) {
    this.streamKey = streamKey;
    this.consumerGroup = consumerGroup;
    this.consumerName = consumerName;
    this.redis = redis;
    this.jdbc = jdbc;
  }

  // =========================================================================
  // Poll loop — runs every 200 ms
  // =========================================================================

  /**
   * One poll iteration: ensures the consumer group exists, re-claims stale PEL messages, then reads
   * up to 10 new messages via {@code XREADGROUP}.
   */
  @Scheduled(fixedDelay = 200)
  public void pollOnce() {
    ensureGroupExists();
    if (!groupInitialized.get()) {
      return;
    }
    reclaimStalePending();
    List<MapRecord<String, Object, Object>> records =
        redis
            .opsForStream()
            .read(
                Consumer.from(consumerGroup, consumerName),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(100)),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
    if (records != null) {
      for (MapRecord<String, Object, Object> record : records) {
        processRecord(record);
      }
    }
  }

  // =========================================================================
  // Consumer group initialisation
  // =========================================================================

  private void ensureGroupExists() {
    if (groupInitialized.get()) {
      return;
    }
    try {
      redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
      groupInitialized.set(true);
    } catch (Exception e) {
      String msg = Objects.toString(e.getMessage(), "");
      if (msg.contains("BUSYGROUP")) {
        groupInitialized.set(true);
      }
      // Stream not yet created by the relay — will retry on the next tick
    }
  }

  // =========================================================================
  // Visibility-timeout claim (PEL re-delivery)
  // =========================================================================

  /**
   * Claims messages from the group's PEL that have been idle for longer than {@link
   * #visibilityTimeout}. If a message has already been delivered {@value #MAX_DELIVERY_ATTEMPTS}
   * times it is dead-lettered; otherwise it is reprocessed by this consumer.
   *
   * <p>TO-DO (§22.5.6): an end-to-end test with an actual 60-s idle window requires stopping a real
   * consumer process mid-handling and is out of scope for the integration-test suite. The {@code
   * visibilityTimeout} field is overrideable in tests via {@link #setVisibilityTimeout(Duration)}.
   */
  private void reclaimStalePending() {
    PendingMessages pending;
    try {
      pending = redis.opsForStream().pending(streamKey, consumerGroup, Range.unbounded(), 100L);
    } catch (Exception e) {
      log.warn(
          "consumer={} stream={} — could not read PEL: {}",
          consumerGroup,
          streamKey,
          e.getMessage());
      return;
    }
    for (PendingMessage msg : pending) {
      Duration idleTime = msg.getElapsedTimeSinceLastDelivery();
      if (idleTime == null || idleTime.compareTo(visibilityTimeout) < 0) {
        continue;
      }
      // Use Duration.ZERO so the claim always succeeds — idle time was already verified above.
      List<MapRecord<String, Object, Object>> claimed =
          redis
              .opsForStream()
              .claim(streamKey, consumerGroup, consumerName, Duration.ZERO, msg.getId());
      if (claimed == null || claimed.isEmpty()) {
        continue;
      }
      for (MapRecord<String, Object, Object> record : claimed) {
        if (msg.getTotalDeliveryCount() >= MAX_DELIVERY_ATTEMPTS) {
          sendToDlqAndAck(record);
        } else {
          processRecord(record);
        }
      }
    }
  }

  // =========================================================================
  // Message processing
  // =========================================================================

  private void processRecord(MapRecord<String, Object, Object> record) {
    Map<Object, Object> body = record.getValue();
    String rawId = (String) body.get("id");
    String rawTenantId = (String) body.get("tenant_id");
    String payload = (String) body.get("payload");

    if (rawId == null || rawTenantId == null || payload == null) {
      log.warn(
          "consumer={} stream={} — malformed record {}, ACK-ing and skipping",
          consumerGroup,
          streamKey,
          record.getId());
      redis.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
      return;
    }

    UUID eventId = UUID.fromString(rawId);
    UUID tenantId = UUID.fromString(rawTenantId);
    String eventType = streamKey.replaceFirst("^events:", "");

    TenantContext.set(tenantId);
    try {
      // Idempotency gate: check if this event was already processed by this consumer group.
      // SELECT EXISTS is required because the PK of the partitioned processed_events table
      // includes processed_at; ON CONFLICT alone cannot detect re-deliveries at different times.
      Boolean seen =
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM processed_events"
                  + " WHERE event_id = ? AND consumer_group = ?)",
              Boolean.class,
              eventId,
              consumerGroup);
      if (Boolean.TRUE.equals(seen)) {
        redis.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        return;
      }

      // Deliver to the subclass handler. On failure, leave the message in the PEL for retry.
      try {
        handle(new EventEnvelope(eventId, tenantId, payload, eventType));
      } catch (Exception handlerEx) {
        log.error(
            "consumer={} eventId={} stream={} — handle() threw; message left in PEL for retry",
            consumerGroup,
            eventId,
            streamKey,
            handlerEx);
        // TO-DO: route to Sentry once TASK-Sentry lands
        return;
      }

      // Mark as processed (ON CONFLICT DO NOTHING handles concurrent exact-timestamp races).
      jdbc.update(
          "INSERT INTO processed_events (event_id, consumer_group, processed_at, tenant_id)"
              + " VALUES (?, ?, now(), ?) ON CONFLICT DO NOTHING",
          eventId,
          consumerGroup,
          tenantId);

      redis.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());

    } catch (Exception e) {
      log.error(
          "consumer={} eventId={} stream={} — unexpected error during processing;"
              + " message left in PEL",
          consumerGroup,
          eventId,
          streamKey,
          e);
    } finally {
      TenantContext.clear();
    }
  }

  private void sendToDlqAndAck(MapRecord<String, Object, Object> record) {
    String eventType = streamKey.replaceFirst("^events:", "");
    String dlqKey = "events:" + eventType + ":dlq";
    try {
      redis.opsForStream().add(dlqKey, toStringMap(record.getValue()));
      redis.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
      log.error(
          "consumer={} stream={} — event {} dead-lettered to {} after {} delivery attempts",
          consumerGroup,
          streamKey,
          record.getValue().get("id"),
          dlqKey,
          MAX_DELIVERY_ATTEMPTS);
      // TO-DO: route to Sentry once TASK-Sentry lands
    } catch (Exception e) {
      log.error(
          "consumer={} stream={} — failed to dead-letter message {}: {}",
          consumerGroup,
          streamKey,
          record.getId(),
          e.getMessage(),
          e);
    }
  }

  private static Map<String, String> toStringMap(Map<Object, Object> body) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<Object, Object> entry : body.entrySet()) {
      result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
    }
    return result;
  }

  // =========================================================================
  // Abstract hook
  // =========================================================================

  /**
   * Processes one event. Implementations must be idempotent. Throwing any exception causes the
   * message to remain in the PEL for retry; after {@value #MAX_DELIVERY_ATTEMPTS} failed deliveries
   * the message is dead-lettered.
   *
   * @param envelope the event envelope containing event ID, tenant ID, payload, and event type
   * @throws Exception if processing fails and the message should be retried
   */
  protected abstract void handle(EventEnvelope envelope) throws Exception;

  // =========================================================================
  // Package-visible accessors for tests
  // =========================================================================

  String getStreamKey() {
    return streamKey;
  }

  String getConsumerGroup() {
    return consumerGroup;
  }

  String getConsumerName() {
    return consumerName;
  }

  /** Overrides the default 60-s visibility timeout. Intended only for test use. */
  void setVisibilityTimeout(Duration timeout) {
    this.visibilityTimeout = timeout;
  }

  /** Resets the consumer-group initialisation flag. Intended only for test use. */
  void resetGroupInitialized() {
    groupInitialized.set(false);
  }
}
