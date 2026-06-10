package com.teamhub.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JobMetrics} (TASK-014).
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Spring context required.
 */
@Tag("unit")
class JobMetricsTest {

  private SimpleMeterRegistry registry;

  private JobMetrics jobMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    jobMetrics = new JobMetrics(registry);
  }

  @Test
  void recordJobCreated_incrementsCounter() {
    jobMetrics.recordJobCreated("test-tenant");

    Counter counter =
        registry.find("teamhub.jobs.created.total").tag("tenant_id", "test-tenant").counter();

    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordJobCreated_multipleIncrements_accumulateCount() {
    jobMetrics.recordJobCreated("tenant-a");
    jobMetrics.recordJobCreated("tenant-a");
    jobMetrics.recordJobCreated("tenant-a");

    Counter counter =
        registry.find("teamhub.jobs.created.total").tag("tenant_id", "tenant-a").counter();

    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
  }

  @Test
  void recordJobCreated_differentTenants_trackedSeparately() {
    jobMetrics.recordJobCreated("tenant-x");
    jobMetrics.recordJobCreated("tenant-y");
    jobMetrics.recordJobCreated("tenant-y");

    Counter counterX =
        registry.find("teamhub.jobs.created.total").tag("tenant_id", "tenant-x").counter();
    Counter counterY =
        registry.find("teamhub.jobs.created.total").tag("tenant_id", "tenant-y").counter();

    assertThat(counterX).isNotNull();
    assertThat(counterX.count()).isEqualTo(1.0);
    assertThat(counterY).isNotNull();
    assertThat(counterY.count()).isEqualTo(2.0);
  }
}
