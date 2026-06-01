package com.teamhub.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobClaimed;
import com.teamhub.eventing.payloads.JobCompleted;
import com.teamhub.eventing.payloads.JobStatusChanged;
import com.teamhub.eventing.payloads.JobSubmitted;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Audit-logger Redis Streams consumer (PRD §19.2, TASK-035).
 *
 * <p>Each instance subscribes to a single stream key; the six subscribed event types are registered
 * as separate Spring beans by {@link AuditEventConsumerConfig}.
 *
 * <p>Idempotency, PEL reclaim, and DLQ routing are fully inherited from {@link
 * AbstractOutboxConsumer} — this class only implements the per-event-type mapping to an {@link
 * AuditLog} entry.
 *
 * <p>{@code PhotoUploaded} and {@code QuotaThresholdReached} are intentionally not subscribed by
 * this consumer.
 */
public class AuditEventConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

  static final String CONSUMER_GROUP = "audit-logger";

  private final AsyncAuditService asyncAuditService;

  private final ObjectMapper objectMapper;

  AuditEventConsumer(
      String streamKey,
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    super(streamKey, CONSUMER_GROUP, consumerName, redis, jdbc);
    this.asyncAuditService = asyncAuditService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    String eventType = envelope.eventType();
    String payload = envelope.payload();
    UUID tenantId = envelope.tenantId();

    AuditLog entry =
        switch (eventType) {
          case "JobSubmitted" -> buildJobSubmitted(tenantId, payload);
          case "JobClaimed" -> buildJobClaimed(tenantId, payload);
          case "JobStatusChanged" -> buildJobStatusChanged(tenantId, payload);
          case "JobCompleted" -> buildJobCompleted(tenantId, payload);
          case "UserInvited" -> buildUserInvited(tenantId, payload);
          case "SubscriptionUpdated" -> buildSubscriptionUpdated(tenantId, payload);
          default -> {
            log.warn("AuditEventConsumer: unexpected eventType={}, skipping", eventType);
            yield null;
          }
        };

    if (entry != null) {
      asyncAuditService.recordAsync(entry);
    }
  }

  private AuditLog buildJobSubmitted(UUID tenantId, String payload) throws Exception {
    JobSubmitted e = objectMapper.readValue(payload, JobSubmitted.class);
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("job.create")
        .resourceType("job")
        .resourceId(e.jobId())
        .afterState(payload)
        .build();
  }

  private AuditLog buildJobClaimed(UUID tenantId, String payload) throws Exception {
    JobClaimed e = objectMapper.readValue(payload, JobClaimed.class);
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("job.claimed")
        .resourceType("job")
        .resourceId(e.jobId())
        .afterState(payload)
        .build();
  }

  private AuditLog buildJobStatusChanged(UUID tenantId, String payload) throws Exception {
    JobStatusChanged e = objectMapper.readValue(payload, JobStatusChanged.class);
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("job.transition")
        .resourceType("job")
        .resourceId(e.jobId())
        .beforeState("{\"status\":\"" + e.oldStatus() + "\"}")
        .afterState("{\"status\":\"" + e.newStatus() + "\"}")
        .build();
  }

  private AuditLog buildJobCompleted(UUID tenantId, String payload) throws Exception {
    JobCompleted e = objectMapper.readValue(payload, JobCompleted.class);
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("job.completed")
        .resourceType("job")
        .resourceId(e.jobId())
        .afterState(payload)
        .build();
  }

  private AuditLog buildUserInvited(UUID tenantId, String payload) throws Exception {
    // invitedBy UUID is the actor, but may not exist in the local users table (event-sourced).
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("user.invited")
        .resourceType("user")
        .afterState(payload)
        .build();
  }

  private AuditLog buildSubscriptionUpdated(UUID tenantId, String payload) {
    return AuditLog.builder()
        .tenantId(tenantId)
        .action("subscription.updated")
        .resourceType("subscription")
        .afterState(payload)
        .build();
  }
}
