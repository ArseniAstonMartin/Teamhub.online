package com.teamhub.escalation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.DelayedJobDispatcher;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobSubmitted;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schedules a 5-minute escalation delayed job when a new job enters the shared pool (PRD §10.3,
 * TASK-046).
 *
 * <p>Consumer group {@value #CONSUMER_GROUP} subscribes to the {@code events:JobSubmitted} stream.
 * For each event it creates a {@code delayed_jobs} row with {@code kind='escalate_unclaimed_job'}
 * and {@code run_at = now() + 5 minutes}, plus the corresponding ZADD to the Redis sorted set via
 * {@link DelayedJobDispatcher#schedule}.
 */
public class EscalationSchedulerConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(EscalationSchedulerConsumer.class);

  static final String CONSUMER_GROUP = "escalation-scheduler";

  static final String JOB_KIND = "escalate_unclaimed_job";

  static final Duration ESCALATION_DELAY = Duration.ofMinutes(5);

  private final DelayedJobDispatcher dispatcher;

  private final ObjectMapper objectMapper;

  EscalationSchedulerConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DelayedJobDispatcher dispatcher,
      ObjectMapper objectMapper) {
    super("events:JobSubmitted", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobSubmitted event = objectMapper.readValue(envelope.payload(), JobSubmitted.class);

    String payload = objectMapper.writeValueAsString(new EscalationPayload(event.jobId()));
    Instant runAt = Instant.now().plus(ESCALATION_DELAY);

    dispatcher.schedule(event.tenantId(), JOB_KIND, payload, runAt);

    log.info(
        "escalation-scheduler: scheduled {} for jobId={} tenantId={} runAt={}",
        JOB_KIND,
        event.jobId(),
        event.tenantId(),
        runAt);
  }
}
