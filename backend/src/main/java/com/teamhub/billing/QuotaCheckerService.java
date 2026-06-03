package com.teamhub.billing;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.QuotaThresholdReached;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional business logic for quota threshold detection (PRD §17.3, §22.5.7, TASK-065).
 *
 * <p>Separated from the consumer class so that JPA saves and outbox publishes participate in the
 * same Spring-managed transaction (the consumer's {@code handle()} is called directly from the
 * superclass, bypassing the Spring proxy).
 */
@Service
public class QuotaCheckerService {

  private static final Logger log = LoggerFactory.getLogger(QuotaCheckerService.class);

  private final TenantRepository tenantRepository;

  private final TenantQuotaStateRepository quotaStateRepository;

  private final OutboxPublisher outboxPublisher;

  public QuotaCheckerService(
      TenantRepository tenantRepository,
      TenantQuotaStateRepository quotaStateRepository,
      OutboxPublisher outboxPublisher) {
    this.tenantRepository = tenantRepository;
    this.quotaStateRepository = quotaStateRepository;
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Increments the job counter for the tenant and fires {@link QuotaThresholdReached} events when
   * the 80 % or 100 % threshold is crossed for the first time in the current billing period.
   *
   * @param tenantId the tenant that submitted a job
   */
  @Transactional
  public void handleJobSubmitted(UUID tenantId) {
    TenantContext.set(tenantId);

    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    Instant periodStart = resolvePeriodStart();

    TenantQuotaState state =
        quotaStateRepository
            .findById(tenantId)
            .orElseGet(() -> new TenantQuotaState(tenantId, periodStart));

    if (!state.getPeriodStart().equals(periodStart)) {
      state.resetForPeriod(periodStart);
    }

    state.setJobCount(state.getJobCount() + 1);
    int jobCount = state.getJobCount();

    int planLimit = PlanCatalog.limitsFor(tenant.getPlan()).jobsPerMonth();

    if (planLimit != Integer.MAX_VALUE) {
      if (jobCount >= (int) (planLimit * 0.8) && !state.isThreshold80Fired()) {
        outboxPublisher.publish(
            "QuotaThresholdReached",
            "Tenant",
            tenantId,
            new QuotaThresholdReached(UUID.randomUUID(), tenantId, 80, jobCount, planLimit));
        state.setThreshold80Fired(true);
        log.info(
            "tenant={} quota threshold=80% jobCount={} limit={}", tenantId, jobCount, planLimit);
      }

      if (jobCount >= planLimit && !state.isThreshold100Fired()) {
        outboxPublisher.publish(
            "QuotaThresholdReached",
            "Tenant",
            tenantId,
            new QuotaThresholdReached(UUID.randomUUID(), tenantId, 100, jobCount, planLimit));
        state.setThreshold100Fired(true);
        log.info(
            "tenant={} quota threshold=100% jobCount={} limit={}", tenantId, jobCount, planLimit);
      }
    }

    state.setUpdatedAt(Instant.now());
    quotaStateRepository.save(state);
  }

  /**
   * Resets the tenant's quota period state when a new Stripe billing period begins.
   *
   * @param tenantId the tenant whose subscription was updated
   */
  @Transactional
  public void handleSubscriptionUpdated(UUID tenantId) {
    Instant newPeriodStart = resolvePeriodStart();
    quotaStateRepository
        .findById(tenantId)
        .ifPresentOrElse(
            state -> {
              state.resetForPeriod(newPeriodStart);
              quotaStateRepository.save(state);
              log.info(
                  "tenant={} quota state reset for new billing period starting {}",
                  tenantId,
                  newPeriodStart);
            },
            () ->
                log.debug(
                    "tenant={} has no quota state row — nothing to reset on SubscriptionUpdated",
                    tenantId));
  }

  private static Instant resolvePeriodStart() {
    return Instant.now()
        .atOffset(ZoneOffset.UTC)
        .with(TemporalAdjusters.firstDayOfMonth())
        .toLocalDate()
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC);
  }
}
