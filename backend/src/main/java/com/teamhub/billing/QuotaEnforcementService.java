package com.teamhub.billing;

import com.teamhub.auth.UserRepository;
import com.teamhub.job.JobRepository;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces per-plan quotas and feature locks at the API boundary (PRD §17.3, TASK-064).
 *
 * <p>All limit values are sourced exclusively from {@link PlanCatalog} — no constants are
 * duplicated in this class.
 *
 * <ul>
 *   <li>{@link #checkJobQuota} — rejects job creation when the monthly job count >= plan limit.
 *   <li>{@link #checkSeatQuota} — rejects invite creation when active user count >= seat limit.
 *   <li>{@link #checkSchemaEditorAccess} — rejects schema mutations on the Free tier.
 * </ul>
 */
@Service
public class QuotaEnforcementService {

  private final TenantRepository tenantRepository;

  private final JobRepository jobRepository;

  private final UserRepository userRepository;

  public QuotaEnforcementService(
      TenantRepository tenantRepository,
      JobRepository jobRepository,
      UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
  }

  /**
   * Checks whether the tenant is allowed to create one more job.
   *
   * <p>The job count window starts at the beginning of the current calendar month when {@code
   * currentPeriodEnd} is not set (Free tier / no active subscription). For tenants with an active
   * Stripe subscription, the same calendar-month window is used because {@code currentPeriodStart}
   * is not stored on the tenant row.
   *
   * @param tenantId the owning tenant
   * @throws QuotaExceededException when the monthly job count has reached the plan limit
   */
  @Transactional(readOnly = true)
  public void checkJobQuota(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    PlanCatalog.PlanLimits limits = PlanCatalog.limitsFor(tenant.getPlan());

    if (limits.jobsPerMonth() == Integer.MAX_VALUE) {
      return;
    }

    Instant periodStart = resolvePeriodStart(tenant);
    long count =
        jobRepository.countByTenantIdAndCreatedAtGreaterThanEqualAndArchivedAtIsNull(
            tenantId, periodStart);

    if (count >= limits.jobsPerMonth()) {
      throw new QuotaExceededException();
    }
  }

  /**
   * Checks whether the tenant is allowed to add one more seat (invite a new user).
   *
   * @param tenantId the owning tenant
   * @throws SeatLimitExceededException when the active user count has reached the plan seat limit
   */
  @Transactional(readOnly = true)
  public void checkSeatQuota(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    PlanCatalog.PlanLimits limits = PlanCatalog.limitsFor(tenant.getPlan());

    if (limits.seats() == Integer.MAX_VALUE) {
      return;
    }

    long activeUsers = userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId);
    if (activeUsers >= limits.seats()) {
      throw new SeatLimitExceededException();
    }
  }

  /**
   * Checks whether the tenant's plan includes access to the custom schema editor.
   *
   * @param tenantId the owning tenant
   * @throws PlanFeatureLockedException when the plan does not include schema editor access
   */
  @Transactional(readOnly = true)
  public void checkSchemaEditorAccess(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    PlanCatalog.PlanLimits limits = PlanCatalog.limitsFor(tenant.getPlan());
    if (!limits.schemaEditorEnabled()) {
      throw new PlanFeatureLockedException("schema_editor");
    }
  }

  /**
   * Checks whether the tenant's plan includes access to advanced reports (per-technician
   * comparisons, custom date ranges — PRD §18.2, TASK-067). Requires Team+ plan (TEAM or
   * ENTERPRISE).
   *
   * @param tenantId the owning tenant
   * @throws PlanFeatureLockedException when the plan does not include advanced reports
   */
  @Transactional(readOnly = true)
  public void checkAdvancedReports(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    PlanCatalog.PlanLimits limits = PlanCatalog.limitsFor(tenant.getPlan());
    if (!limits.advancedReportsEnabled()) {
      throw new PlanFeatureLockedException("advanced_reports");
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Resolves the quota window start. Uses the beginning of the current calendar month when no
   * Stripe billing period is active.
   */
  private static Instant resolvePeriodStart(Tenant tenant) {
    if (tenant.getCurrentPeriodEnd() != null) {
      // Approximate the period start as the beginning of the current calendar month.
      // A dedicated currentPeriodStart column would allow an exact calculation (TO-DO: PRD §17.3).
      return startOfCurrentMonth();
    }
    return startOfCurrentMonth();
  }

  private static Instant startOfCurrentMonth() {
    return Instant.now()
        .atOffset(ZoneOffset.UTC)
        .with(TemporalAdjusters.firstDayOfMonth())
        .toLocalDate()
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC);
  }
}
