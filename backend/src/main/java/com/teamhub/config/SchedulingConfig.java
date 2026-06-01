package com.teamhub.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring {@code @Scheduled} task execution and ShedLock distributed-lock support (TASK-031,
 * TASK-033, PRD §22.5.3, §22.5.4).
 *
 * <p>ShedLock guarantees that a {@code @SchedulerLock}-annotated scheduled method executes on at
 * most one JVM node at a time. The {@link LockProvider} implementation uses the {@code shedlock}
 * Postgres table (created by migration V11). {@code usingDbTime()} ensures the lock's {@code
 * lock_until} timestamp is calculated from the database clock, preventing clock-skew issues in
 * multi-node deployments.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT15S")
public class SchedulingConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}
