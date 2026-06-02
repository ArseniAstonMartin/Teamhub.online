package com.teamhub.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for job lifecycle events (PRD §21.5, TASK-014).
 *
 * <p>The counter {@code teamhub.jobs.created.total} is tagged with {@code tenant_id} so Prometheus
 * queries can aggregate or filter per tenant.
 *
 * <p>// TO-DO (TASK-014): {@link #recordJobCreated(String)} is wired into {@link
 * com.teamhub.job.JobService#create} — counter increments on every successful job creation.
 */
@Component
public class JobMetrics {

  private final MeterRegistry registry;

  public JobMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Increments the {@code teamhub.jobs.created.total} counter for the given tenant.
   *
   * @param tenantId the tenant for which a job was created
   */
  public void recordJobCreated(String tenantId) {
    Counter.builder("teamhub.jobs.created.total")
        .tag("tenant_id", tenantId)
        .register(registry)
        .increment();
  }
}
