package com.teamhub.eventing;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency de-dup guard for outbox event consumers (PRD §22.5.6).
 *
 * <p>The first action of any consumer handler is:
 *
 * <pre>
 *   INSERT INTO processed_events (event_id, consumer_group, processed_at, tenant_id)
 *   VALUES (?, ?, now(), ?)
 *   ON CONFLICT DO NOTHING
 * </pre>
 *
 * If zero rows were inserted the event has already been handled; the consumer ACKs immediately.
 *
 * <p>The table is partitioned by month on {@code processed_at}. Old partitions are dropped after
 * the corresponding events age out of the Redis Stream (default 30 days), per PRD §22.5.6. Because
 * of the partitioning requirement, the physical PRIMARY KEY is {@code (event_id, consumer_group,
 * processed_at)}.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

  @EmbeddedId private ProcessedEventId id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  protected ProcessedEvent() {}

  public ProcessedEvent(UUID eventId, String consumerGroup, Instant processedAt, UUID tenantId) {
    this.id = new ProcessedEventId(eventId, consumerGroup, processedAt);
    this.tenantId = tenantId;
  }

  public ProcessedEventId getId() {
    return id;
  }

  public UUID getEventId() {
    return id.getEventId();
  }

  public String getConsumerGroup() {
    return id.getConsumerGroup();
  }

  public Instant getProcessedAt() {
    return id.getProcessedAt();
  }

  public UUID getTenantId() {
    return tenantId;
  }
}
