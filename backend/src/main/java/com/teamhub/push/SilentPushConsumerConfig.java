package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers the {@link SilentPushConsumer} Spring bean (PRD §15.2, TASK-058).
 *
 * <p>Wires {@link SilentPushConsumer}, which subscribes to the {@code events:JobClaimed} Redis
 * stream under consumer group {@value SilentPushConsumer#CONSUMER_GROUP}. {@link ExpoPushClient} is
 * provided by {@link PushFanOutConsumerConfig}.
 */
@Configuration
public class SilentPushConsumerConfig {

  private static final String CONSUMER_NAME = "silent-push-1";

  @Bean
  SilentPushConsumer silentPushConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    return new SilentPushConsumer(
        CONSUMER_NAME, redis, jdbc, deviceTokenRepository, expoPushClient, objectMapper);
  }
}
