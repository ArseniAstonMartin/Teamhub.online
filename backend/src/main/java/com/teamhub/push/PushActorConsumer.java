package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobStatusChanged;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Consumer that sends a push notification to the technician when their job moves from {@code
 * DONE_PENDING_APPROVAL} to {@code COMPLETED} (PRD §15.2, TASK-059).
 *
 * <p>Consumer group {@value #CONSUMER_GROUP} subscribes to the {@code events:JobStatusChanged}
 * stream. For each event it checks the transition filter, looks up the assigned technician's
 * non-stale device tokens, and sends a targeted push via {@link ExpoPushClient#sendBatch(List)}.
 *
 * <p>Events for other transitions are silently acknowledged (no push needed). The handler is
 * idempotent: the base-class de-dup gate in {@link AbstractOutboxConsumer} ensures at-most-once
 * push delivery per event.
 */
public class PushActorConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(PushActorConsumer.class);

  static final String CONSUMER_GROUP = "push-actor";

  private static final String FROM_STATUS = "DONE_PENDING_APPROVAL";

  private static final String TO_STATUS = "COMPLETED";

  private final DeviceTokenRepository deviceTokenRepository;

  private final JobRepository jobRepository;

  private final ExpoPushClient expoPushClient;

  private final ObjectMapper objectMapper;

  PushActorConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      JobRepository jobRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    super("events:JobStatusChanged", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.deviceTokenRepository = deviceTokenRepository;
    this.jobRepository = jobRepository;
    this.expoPushClient = expoPushClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobStatusChanged event = objectMapper.readValue(envelope.payload(), JobStatusChanged.class);

    if (!FROM_STATUS.equals(event.oldStatus()) || !TO_STATUS.equals(event.newStatus())) {
      log.debug(
          "push-actor: skipping transition {}→{} for jobId={}",
          event.oldStatus(),
          event.newStatus(),
          event.jobId());
      return;
    }

    Optional<Job> jobOpt =
        jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(event.jobId(), event.tenantId());
    if (jobOpt.isEmpty()) {
      log.warn(
          "push-actor: job not found or archived jobId={} tenantId={}",
          event.jobId(),
          event.tenantId());
      return;
    }

    UUID technicianId = jobOpt.get().getAssignedTechnicianId();
    if (technicianId == null) {
      log.warn("push-actor: job {} has no assigned technician — skipping push", event.jobId());
      return;
    }

    List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndStaleIsFalse(technicianId);
    if (tokens.isEmpty()) {
      log.debug(
          "push-actor: no active tokens for technicianId={} jobId={}", technicianId, event.jobId());
      return;
    }

    String shortId = event.jobId().toString().substring(0, 8);
    String body = "Job " + shortId + " approved";

    List<PushMessage> messages =
        tokens.stream()
            .map(
                token ->
                    new PushMessage(
                        token.getExpoToken(),
                        "Job Approved",
                        body,
                        Map.of("jobId", event.jobId().toString(), "screen", "JobDetail"),
                        "jobs"))
            .collect(Collectors.toList());

    List<PushTicket> tickets = expoPushClient.sendBatch(messages);

    log.info(
        "push-actor: sent {} messages for jobId={} technicianId={}, received {} tickets",
        messages.size(),
        event.jobId(),
        technicianId,
        tickets.size());
  }
}
