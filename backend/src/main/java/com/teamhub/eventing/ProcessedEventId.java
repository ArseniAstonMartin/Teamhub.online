package com.teamhub.eventing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ProcessedEvent}.
 *
 * <p>PostgreSQL declarative range partitioning on {@code processed_at} requires the partition key
 * to be part of every unique constraint. The logical de-dup key is {@code (event_id,
 * consumer_group)}; the physical PK is {@code (event_id, consumer_group, processed_at)}.
 */
@Embeddable
public class ProcessedEventId implements Serializable {

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "consumer_group", nullable = false)
  private String consumerGroup;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedEventId() {}

  public ProcessedEventId(UUID eventId, String consumerGroup, Instant processedAt) {
    this.eventId = eventId;
    this.consumerGroup = consumerGroup;
    this.processedAt = processedAt;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getConsumerGroup() {
    return consumerGroup;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessedEventId)) {
      return false;
    }
    ProcessedEventId other = (ProcessedEventId) o;
    return Objects.equals(eventId, other.eventId)
        && Objects.equals(consumerGroup, other.consumerGroup)
        && Objects.equals(processedAt, other.processedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, consumerGroup, processedAt);
  }
}
