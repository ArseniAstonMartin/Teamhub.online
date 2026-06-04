package com.teamhub.escalation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.eventing.DelayedJobDispatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers the escalation consumer beans (PRD §10.3, TASK-046).
 *
 * <ul>
 *   <li>{@link EscalationSchedulerConsumer} — listens to {@code events:JobSubmitted} and schedules
 *       an {@code escalate_unclaimed_job} delayed job for 5 minutes in the future.
 *   <li>{@link EscalationCancelerConsumer} — listens to {@code events:JobClaimed} and cancels the
 *       matching pending delayed job so the escalation does not fire.
 * </ul>
 *
 * <p>{@link EscalateUnclaimedJobHandler} is discovered automatically by Spring via
 * {@code @Component}; this class does not need to declare it as a bean.
 */
@Configuration
public class EscalationConfig {

  private static final String SCHEDULER_CONSUMER_NAME = "escalation-scheduler-1";

  private static final String CANCELER_CONSUMER_NAME = "escalation-canceler-1";

  @Bean
  EscalationSchedulerConsumer escalationSchedulerConsumer(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DelayedJobDispatcher dispatcher,
      ObjectMapper objectMapper) {
    return new EscalationSchedulerConsumer(
        SCHEDULER_CONSUMER_NAME, redis, jdbc, dispatcher, objectMapper);
  }

  @Bean
  EscalationCancelerConsumer escalationCancelerConsumer(
      StringRedisTemplate redis, JdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new EscalationCancelerConsumer(CANCELER_CONSUMER_NAME, redis, jdbc, objectMapper);
  }
}
