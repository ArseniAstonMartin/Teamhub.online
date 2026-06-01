package com.teamhub.eventing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox event row (PRD §22, §22.5.3).
 *
 * <p>Every domain event is written atomically with its originating business row in the same DB
 * transaction. The Outbox Relay reads unprocessed rows (WHERE processed_at IS NULL) using the relay
 * scan index and XADDs them to a Redis Stream. After a successful XADD, the relay sets {@code
 * processed_at = now()}.
 *
 * <p>Consumers must never hold a reference to a relay-visible row for longer than the relay's
 * polling window. Use {@link OutboxEventRepository} to INSERT; never UPDATE rows directly — the
 * relay owns the {@code processed_at} field.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  protected OutboxEvent() {}

  private OutboxEvent(Builder builder) {
    this.tenantId = builder.tenantId;
    this.eventType = builder.eventType;
    this.payload = builder.payload;
    this.aggregateType = builder.aggregateType;
    this.aggregateId = builder.aggregateId;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    this.attemptCount = 0;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private UUID tenantId;

    private String eventType;

    private String payload;

    private String aggregateType;

    private UUID aggregateId;

    private Instant createdAt;

    private Builder() {}

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    public Builder payload(String payload) {
      this.payload = payload;
      return this;
    }

    public Builder aggregateType(String aggregateType) {
      this.aggregateType = aggregateType;
      return this;
    }

    public Builder aggregateId(UUID aggregateId) {
      this.aggregateId = aggregateId;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public OutboxEvent build() {
      if (tenantId == null) {
        throw new IllegalStateException("tenantId is required");
      }
      if (eventType == null || eventType.isBlank()) {
        throw new IllegalStateException("eventType is required");
      }
      if (payload == null) {
        throw new IllegalStateException("payload is required");
      }
      if (aggregateType == null || aggregateType.isBlank()) {
        throw new IllegalStateException("aggregateType is required");
      }
      if (aggregateId == null) {
        throw new IllegalStateException("aggregateId is required");
      }
      return new OutboxEvent(this);
    }
  }
}
