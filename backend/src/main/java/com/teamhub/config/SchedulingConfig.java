package com.teamhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring {@code @Scheduled} task execution (TASK-031, PRD §22.5.3).
 *
 * <p>Required for the {@link com.teamhub.eventing.OutboxRelay} polling loop and leader-election
 * heartbeat to fire.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
