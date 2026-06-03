package com.teamhub.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers the {@link QuotaCheckerConsumer} and {@link SubscriptionUpdatedConsumer} Spring beans
 * (PRD §22.5.7, TASK-065).
 */
@Configuration
public class QuotaCheckerConsumerConfig {

  private static final String QUOTA_CHECKER_CONSUMER_NAME = "quota-checker-1";

  private static final String QUOTA_RESETTER_CONSUMER_NAME = "quota-resetter-1";

  @Bean
  QuotaCheckerConsumer quotaCheckerConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      QuotaCheckerService quotaCheckerService,
      ObjectMapper objectMapper) {
    return new QuotaCheckerConsumer(
        QUOTA_CHECKER_CONSUMER_NAME, redis, jdbc, quotaCheckerService, objectMapper);
  }

  @Bean
  SubscriptionUpdatedConsumer subscriptionUpdatedConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      QuotaCheckerService quotaCheckerService,
      ObjectMapper objectMapper) {
    return new SubscriptionUpdatedConsumer(
        QUOTA_RESETTER_CONSUMER_NAME, redis, jdbc, quotaCheckerService, objectMapper);
  }
}
