package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobClaimed;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Consumer that fires a silent badge-update push to all OTHER Technicians in the tenant when a job
 * is claimed (PRD §15.2, TASK-058).
 *
 * <p>Consumer group {@value #CONSUMER_GROUP} subscribes to the {@code events:JobClaimed} stream.
 * For each event it loads non-stale TECHNICIAN tokens, excludes the claiming technician, builds a
 * silent {@link PushMessage} per remaining token, and calls {@link ExpoPushClient#sendBatch(List)}.
 * The silent push carries only a {@code data} payload — no title, no body, no sound — signaling the
 * mobile app to invalidate its shared-pool query cache.
 */
public class SilentPushConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(SilentPushConsumer.class);

  static final String CONSUMER_GROUP = "silent-push-other-techs";

  private final DeviceTokenRepository deviceTokenRepository;

  private final ExpoPushClient expoPushClient;

  private final ObjectMapper objectMapper;

  SilentPushConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    super("events:JobClaimed", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.deviceTokenRepository = deviceTokenRepository;
    this.expoPushClient = expoPushClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobClaimed event = objectMapper.readValue(envelope.payload(), JobClaimed.class);

    List<DeviceToken> tokens =
        deviceTokenRepository.findByTenantIdAndUserRoleAndStaleIsFalse(
            event.tenantId(), "TECHNICIAN");

    List<DeviceToken> recipients =
        tokens.stream()
            .filter(token -> !token.getUserId().equals(event.technicianId()))
            .collect(Collectors.toList());

    if (recipients.isEmpty()) {
      log.debug(
          "silent-push: no recipients for tenant={} jobId={} claimer={}",
          event.tenantId(),
          event.jobId(),
          event.technicianId());
      return;
    }

    List<PushMessage> messages =
        recipients.stream()
            .map(
                token ->
                    new PushMessage(
                        token.getExpoToken(),
                        null,
                        null,
                        Map.of("type", "SHARED_POOL_REFRESH", "jobId", event.jobId().toString()),
                        "silent"))
            .collect(Collectors.toList());

    List<PushTicket> tickets = expoPushClient.sendBatch(messages);

    log.info(
        "silent-push: sent {} silent messages for jobId={} tenantId={}, received {} tickets",
        messages.size(),
        event.jobId(),
        event.tenantId(),
        tickets.size());
  }
}
