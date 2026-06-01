package com.teamhub.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers one {@link AuditEventConsumer} Spring bean per subscribed event type (PRD §22.5.5,
 * TASK-035).
 *
 * <p>Each bean independently polls its own Redis Stream every 200 ms via the {@code @Scheduled
 * pollOnce()} method inherited from {@link com.teamhub.eventing.AbstractOutboxConsumer}. All
 * instances share consumer group {@value AuditEventConsumer#CONSUMER_GROUP} and consumer name
 * {@value #CONSUMER_NAME}, so Redis delivers each stream's messages to exactly one instance at a
 * time.
 *
 * <p>Intentionally omitted streams: {@code events:PhotoUploaded}, {@code
 * events:QuotaThresholdReached}.
 */
@Configuration
public class AuditEventConsumerConfig {

  private static final String CONSUMER_NAME = "audit-logger-1";

  @Bean
  AuditEventConsumer auditConsumerJobSubmitted(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:JobSubmitted", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }

  @Bean
  AuditEventConsumer auditConsumerJobClaimed(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:JobClaimed", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }

  @Bean
  AuditEventConsumer auditConsumerJobStatusChanged(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:JobStatusChanged", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }

  @Bean
  AuditEventConsumer auditConsumerJobCompleted(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:JobCompleted", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }

  @Bean
  AuditEventConsumer auditConsumerUserInvited(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:UserInvited", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }

  @Bean
  AuditEventConsumer auditConsumerSubscriptionUpdated(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    return new AuditEventConsumer(
        "events:SubscriptionUpdated", CONSUMER_NAME, redis, jdbc, asyncAuditService, objectMapper);
  }
}
