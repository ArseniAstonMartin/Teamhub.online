package com.teamhub.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-tenant quota period state used by the {@code quota-checker} consumer (TASK-065, PRD §17.3).
 *
 * <p>One row per tenant; reset when the billing period advances. Tracks how many jobs have been
 * submitted in the current period and whether the 80 %/100 % threshold events have already fired.
 */
@Entity
@Table(name = "tenant_quota_state")
@Getter
@Setter
@NoArgsConstructor
public class TenantQuotaState {

  @Id
  @Column(name = "tenant_id", updatable = false, nullable = false)
  private UUID tenantId;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "job_count", nullable = false)
  private int jobCount = 0;

  @Column(name = "threshold80_fired", nullable = false)
  private boolean threshold80Fired = false;

  @Column(name = "threshold100_fired", nullable = false)
  private boolean threshold100Fired = false;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public TenantQuotaState(UUID tenantId, Instant periodStart) {
    this.tenantId = tenantId;
    this.periodStart = periodStart;
  }

  /** Resets counters and threshold flags for a new billing period. */
  public void resetForPeriod(Instant newPeriodStart) {
    this.periodStart = newPeriodStart;
    this.jobCount = 0;
    this.threshold80Fired = false;
    this.threshold100Fired = false;
    this.updatedAt = Instant.now();
  }
}
