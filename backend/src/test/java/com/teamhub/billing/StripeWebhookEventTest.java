package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StripeWebhookEvent} getters (TASK-063).
 *
 * <p>The entity uses field-based JPA access so Hibernate never calls these getters during
 * persistence; they are only exercised by application code that reads the record after creation.
 */
@Tag("unit")
class StripeWebhookEventTest {

  @Test
  void getters_returnValuesPassedToConstructor() {
    Instant now = Instant.now();
    StripeWebhookEvent event =
        new StripeWebhookEvent("evt_test_123", "customer.subscription.updated", now);

    assertThat(event.getId()).isEqualTo("evt_test_123");
    assertThat(event.getEventType()).isEqualTo("customer.subscription.updated");
    assertThat(event.getProcessedAt()).isEqualTo(now);
  }
}
