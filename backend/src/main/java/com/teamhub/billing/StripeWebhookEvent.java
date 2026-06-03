package com.teamhub.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Records each processed Stripe webhook event ID for idempotency (TASK-063, PRD §17.3).
 *
 * <p>Before handling any webhook event the handler checks {@code existsById(event.getId())}. If a
 * row already exists the event was already processed and the handler exits early. The row is saved
 * inside the same {@code @Transactional} boundary as the tenant state mutation.
 *
 * <p>This table is NOT tenant-scoped: Stripe event IDs are globally unique.
 */
@Entity
@Table(name = "stripe_webhook_events")
public class StripeWebhookEvent {

  @Id private String id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected StripeWebhookEvent() {}

  public StripeWebhookEvent(String id, String eventType, Instant processedAt) {
    this.id = id;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public String getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
