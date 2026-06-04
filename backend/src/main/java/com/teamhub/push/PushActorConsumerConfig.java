package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.job.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers the {@link PushActorConsumer} Spring bean (PRD §15.2, TASK-059).
 *
 * <p>Wires {@link PushActorConsumer}, which subscribes to the {@code events:JobStatusChanged} Redis
 * stream under consumer group {@value PushActorConsumer#CONSUMER_GROUP}. {@link ExpoPushClient} is
 * provided by {@link PushFanOutConsumerConfig}.
 */
@Configuration
public class PushActorConsumerConfig {

  private static final String CONSUMER_NAME = "push-actor-1";

  @Bean
  PushActorConsumer pushActorConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      JobRepository jobRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    return new PushActorConsumer(
        CONSUMER_NAME,
        redis,
        jdbc,
        deviceTokenRepository,
        jobRepository,
        expoPushClient,
        objectMapper);
  }
}
