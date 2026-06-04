package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.AbstractOutboxConsumer;
import com.teamhub.eventing.EventEnvelope;
import com.teamhub.eventing.payloads.JobSubmitted;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fan-out consumer that sends Expo push notifications to all active Technicians in a tenant when a
 * new job is submitted (PRD §15.2, TASK-057).
 *
 * <p>Consumer group {@value #CONSUMER_GROUP} subscribes to the {@code events:JobSubmitted} stream.
 * For each event it loads non-stale TECHNICIAN tokens, builds a push message per token, and calls
 * {@link ExpoPushClient#sendBatch(List)}. The method is not transactional — push is a side effect.
 */
public class PushFanOutConsumer extends AbstractOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(PushFanOutConsumer.class);

  static final String CONSUMER_GROUP = "push-fan-out-technicians";

  private final DeviceTokenRepository deviceTokenRepository;

  private final ExpoPushClient expoPushClient;

  private final ObjectMapper objectMapper;

  PushFanOutConsumer(
      String consumerName,
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    super("events:JobSubmitted", CONSUMER_GROUP, consumerName, redis, jdbc);
    this.deviceTokenRepository = deviceTokenRepository;
    this.expoPushClient = expoPushClient;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void handle(EventEnvelope envelope) throws Exception {
    JobSubmitted event = objectMapper.readValue(envelope.payload(), JobSubmitted.class);

    List<DeviceToken> tokens =
        deviceTokenRepository.findByTenantIdAndUserRoleAndStaleIsFalse(
            event.tenantId(), "TECHNICIAN");

    if (tokens.isEmpty()) {
      log.debug(
          "push-fan-out: no active TECHNICIAN tokens for tenant={} jobId={}",
          event.tenantId(),
          event.jobId());
      return;
    }

    List<PushMessage> messages =
        tokens.stream()
            .map(
                token ->
                    new PushMessage(
                        token.getExpoToken(),
                        "New Job Available",
                        "New job: " + event.serviceType(),
                        Map.of("jobId", event.jobId().toString(), "screen", "JobDetail"),
                        "jobs"))
            .collect(Collectors.toList());

    List<PushTicket> tickets = expoPushClient.sendBatch(messages);

    log.info(
        "push-fan-out: sent {} messages for jobId={} tenantId={}, received {} tickets",
        messages.size(),
        event.jobId(),
        event.tenantId(),
        tickets.size());
  }
}
