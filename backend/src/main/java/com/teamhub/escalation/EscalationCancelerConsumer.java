package com.teamhub.escalation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobClaimed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cancels the pending escalation delayed job when a job is claimed by a technician (PRD §10.3,
 * TASK-046).
 *
 * <p>Consumer group {@value #CONSUMER_GROUP} subscribes to the {@code events:JobClaimed} stream.
 * For each event it sets the matching {@code delayed_jobs} row to {@code status='CANCELLED'},
 * preventing the escalation push from firing once the job has been picked up.
 *
 * <p>Cancellation is intentionally best-effort: if the dispatcher has already picked up the job
 * from Redis and removed it from the sorted set before this consumer runs, the CANCELLED status
 * will still be read by the dispatcher's pre-run check, which marks the row {@code DONE} without
 * invoking the handler.
 */
public class EscalationCancelerConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(EscalationCancelerConsumer.class);

  static final String CONSUMER_GROUP = "escalation-canceler";

  private final JdbcTemplate cancelerJdbc;

  private final ObjectMapper objectMapper;

  EscalationCancelerConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    super("events:JobClaimed", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.cancelerJdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobClaimed event = objectMapper.readValue(envelope.payload(), JobClaimed.class);

    int rows =
        cancelerJdbc.update(
            """
            UPDATE delayed_jobs
               SET status = 'CANCELLED'
             WHERE kind = ?
               AND status = 'PENDING'
               AND payload ->> 'jobId' = ?
            """,
            EscalationSchedulerConsumer.JOB_KIND,
            event.jobId().toString());

    log.info(
        "escalation-canceler: cancelled {} row(s) for jobId={} tenantId={}",
        rows,
        event.jobId(),
        event.tenantId());
  }
}
