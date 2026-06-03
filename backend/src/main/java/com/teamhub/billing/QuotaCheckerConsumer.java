package com.teamhub.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobSubmitted;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Redis Streams consumer that tracks per-tenant job counts and fires {@link
 * com.teamhub.eventing.payloads.QuotaThresholdReached} cascading events at 80 % and 100 % of the
 * monthly quota (PRD §17.3, §22.5.7, TASK-065).
 *
 * <p>Business logic is delegated to {@link QuotaCheckerService} so that JPA saves and outbox
 * publishes participate in the same Spring-managed transaction. Idempotency, PEL reclaim and DLQ
 * routing are inherited from {@link AbstractOutboxConsumer}.
 */
public class QuotaCheckerConsumer extends AbstractOutboxConsumer {

  static final String CONSUMER_GROUP = "quota-checker";

  private final QuotaCheckerService quotaCheckerService;

  private final ObjectMapper objectMapper;

  QuotaCheckerConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      QuotaCheckerService quotaCheckerService,
      ObjectMapper objectMapper) {
    super("events:JobSubmitted", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.quotaCheckerService = quotaCheckerService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobSubmitted event = objectMapper.readValue(envelope.payload(), JobSubmitted.class);
    UUID tenantId = envelope.tenantId();
    quotaCheckerService.handleJobSubmitted(tenantId);
  }
}
