package com.teamhub.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Registers push-notification Spring beans (PRD §15.2, TASK-057).
 *
 * <p>Wires {@link ExpoPushClient} (backed by Spring {@link RestClient}) and {@link
 * PushFanOutConsumer}, which subscribes to the {@code events:JobSubmitted} Redis stream under
 * consumer group {@value PushFanOutConsumer#CONSUMER_GROUP}.
 */
@Configuration
@EnableConfigurationProperties(ExpoPushProperties.class)
public class PushFanOutConsumerConfig {

  private static final String CONSUMER_NAME = "push-fan-out-1";

  @Bean
  ExpoPushClient expoPushClient(
      ExpoPushProperties props, ObjectMapper objectMapper, DeviceTokenService deviceTokenService) {
    RestClient restClient =
        RestClient.builder()
            .baseUrl(props.expoPushUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ExpoPushClient(restClient, objectMapper, deviceTokenService, props);
  }

  @Bean
  PushFanOutConsumer pushFanOutConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DeviceTokenRepository deviceTokenRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper) {
    return new PushFanOutConsumer(
        CONSUMER_NAME, redis, jdbc, deviceTokenRepository, expoPushClient, objectMapper);
  }
}
