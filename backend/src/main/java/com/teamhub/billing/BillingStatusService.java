package com.teamhub.billing;

import com.teamhub.auth.UserRepository;
import com.teamhub.billing.dto.BillingStatusResponse;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only billing status for the Settings → Billing page (PRD §17, TASK-125).
 *
 * <p>Aggregates subscription fields from {@code tenants} and usage counters from {@code
 * tenant_quota_state}.
 */
@Service
public class BillingStatusService {

  private final TenantRepository tenantRepository;

  private final TenantQuotaStateRepository quotaStateRepository;

  private final UserRepository userRepository;

  public BillingStatusService(
      TenantRepository tenantRepository,
      TenantQuotaStateRepository quotaStateRepository,
      UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.quotaStateRepository = quotaStateRepository;
    this.userRepository = userRepository;
  }

  /**
   * Returns the caller's tenant billing status snapshot.
   *
   * @param tenantId the authenticated tenant
   */
  @Transactional(readOnly = true)
  public BillingStatusResponse getStatus(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

    int jobsThisPeriod =
        quotaStateRepository.findById(tenantId).map(TenantQuotaState::getJobCount).orElse(0);

    int seats = (int) userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId);

    Instant paymentFailedAt = tenant.getPaymentFailedAt();

    return new BillingStatusResponse(
        tenant.getPlan(),
        resolvePeriodEnd(tenant),
        paymentFailedAt != null,
        paymentFailedAt,
        jobsThisPeriod,
        seats);
  }

  private static Instant resolvePeriodEnd(Tenant tenant) {
    if (tenant.getCurrentPeriodEnd() != null) {
      return tenant.getCurrentPeriodEnd();
    }
    return Instant.now()
        .atOffset(ZoneOffset.UTC)
        .with(TemporalAdjusters.firstDayOfNextMonth())
        .toLocalDate()
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .minusSeconds(1);
  }
}
