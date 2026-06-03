package com.teamhub.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.SubscriptionUpdated;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Redis Streams consumer that resets quota period state when the Stripe billing period rolls over
 * (PRD §17.3, §22.5.7, TASK-065).
 *
 * <p>Business logic is delegated to {@link QuotaCheckerService} so that JPA saves participate in a
 * Spring-managed transaction. Idempotency, PEL reclaim and DLQ routing are inherited from {@link
 * AbstractOutboxConsumer}.
 */
public class SubscriptionUpdatedConsumer extends AbstractOutboxConsumer {

  static final String CONSUMER_GROUP = "quota-resetter";

  private final QuotaCheckerService quotaCheckerService;

  private final ObjectMapper objectMapper;

  SubscriptionUpdatedConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      QuotaCheckerService quotaCheckerService,
      ObjectMapper objectMapper) {
    super("events:SubscriptionUpdated", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.quotaCheckerService = quotaCheckerService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    SubscriptionUpdated event =
        objectMapper.readValue(envelope.payload(), SubscriptionUpdated.class);
    UUID tenantId = envelope.tenantId();
    quotaCheckerService.handleSubscriptionUpdated(tenantId);
  }
}
