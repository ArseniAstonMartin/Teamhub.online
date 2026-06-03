package com.teamhub.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.RequestOptions;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Processes a batch of tenants against the Stripe API for plan/period reconciliation (TASK-066, PRD
 * §28).
 *
 * <p>Each batch runs asynchronously on a shared task executor so that the daily reconciliation job
 * can process multiple batches in parallel and meet the PRD §24.2 performance target of 200 tenants
 * in under 5 minutes.
 */
@Component
class ReconciliationBatchRunner {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationBatchRunner.class);

  private static final long PERIOD_DRIFT_THRESHOLD_SECONDS = 60L;

  private final StripePriceProperties properties;

  private final TenantRepository tenantRepository;

  private final SubscriptionFetcher subscriptionFetcher;

  ReconciliationBatchRunner(
      StripePriceProperties properties,
      TenantRepository tenantRepository,
      SubscriptionFetcher subscriptionFetcher) {
    this.properties = properties;
    this.tenantRepository = tenantRepository;
    this.subscriptionFetcher = subscriptionFetcher;
  }

  /**
   * Calls {@code Subscription.retrieve} for every tenant in {@code batch} and persists any detected
   * drift.
   *
   * @param batch tenants to process in this execution unit
   * @param requestOptions Stripe request options (API version pinned to 2026-04-22.dahlia)
   * @return a completed future when all tenants in the batch have been processed
   */
  @Async
  CompletableFuture<Void> processBatch(List<Tenant> batch, RequestOptions requestOptions) {
    for (Tenant tenant : batch) {
      processTenant(tenant, requestOptions);
    }
    return CompletableFuture.completedFuture(null);
  }

  private void processTenant(Tenant tenant, RequestOptions requestOptions) {
    try {
      com.stripe.model.Subscription subscription =
          subscriptionFetcher.fetch(tenant.getStripeSubscriptionId(), requestOptions);

      Optional<Plan> resolvedPlanOpt = extractPlan(subscription);
      if (resolvedPlanOpt.isEmpty()) {
        log.warn(
            "StripeReconciliationJob: unknown price ID for tenant={} sub={} — skipping",
            tenant.getId(),
            tenant.getStripeSubscriptionId());
        return;
      }

      Plan resolvedPlan = resolvedPlanOpt.get();
      Instant resolvedPeriodEnd = extractCurrentPeriodEnd(subscription).orElse(null);

      boolean planDrifted = !resolvedPlan.equals(tenant.getPlan());
      boolean periodDrifted = isPeriodDrifted(tenant.getCurrentPeriodEnd(), resolvedPeriodEnd);

      if (planDrifted || periodDrifted) {
        Plan oldPlan = tenant.getPlan();
        tenant.setPlan(resolvedPlan);
        if (resolvedPeriodEnd != null) {
          tenant.setCurrentPeriodEnd(resolvedPeriodEnd);
        }
        tenantRepository.save(tenant);
        log.warn(
            "SubscriptionDrift: tenant={} expected plan={} got plan={}",
            tenant.getId(),
            oldPlan,
            resolvedPlan);
        // TO-DO (Sentry): send Sentry breadcrumb when Sentry integration is available
      }
    } catch (StripeException e) {
      log.warn(
          "StripeReconciliationJob: failed to retrieve subscription for tenant={} sub={}: {}",
          tenant.getId(),
          tenant.getStripeSubscriptionId(),
          e.getMessage());
    }
  }

  /**
   * Resolves the {@link Plan} from the first subscription item's price ID by matching against the
   * configured {@link StripePriceProperties} price IDs.
   *
   * <p>Returns {@link Optional#empty()} when the price ID is unknown, signalling that the tenant
   * should be skipped rather than overwritten with incorrect data.
   */
  private Optional<Plan> extractPlan(com.stripe.model.Subscription subscription) {
    try {
      List<SubscriptionItem> items = subscription.getItems().getData();
      if (items == null || items.isEmpty()) {
        return Optional.empty();
      }
      com.stripe.model.Price price = items.get(0).getPrice();
      if (price == null) {
        return Optional.empty();
      }
      return resolvePlanFromPriceId(price.getId());
    } catch (Exception e) {
      log.warn(
          "StripeReconciliationJob: could not extract plan from subscription {}: {}",
          subscription.getId(),
          e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<Plan> resolvePlanFromPriceId(String priceId) {
    if (priceId == null) {
      return Optional.empty();
    }
    if (priceId.equals(properties.priceFree())) {
      return Optional.of(Plan.FREE);
    }
    if (priceId.equals(properties.priceStarter())) {
      return Optional.of(Plan.STARTER);
    }
    if (priceId.equals(properties.priceTeam())) {
      return Optional.of(Plan.TEAM);
    }
    if (priceId.equals(properties.priceEnterprise())) {
      return Optional.of(Plan.ENTERPRISE);
    }
    return Optional.empty();
  }

  /**
   * Extracts {@code current_period_end} from the first subscription item.
   *
   * <p>In Stripe API 2026-04-22.dahlia, {@code current_period_end} lives on {@link
   * SubscriptionItem}, not on the top-level {@link Subscription} object.
   */
  private Optional<Instant> extractCurrentPeriodEnd(com.stripe.model.Subscription subscription) {
    try {
      List<SubscriptionItem> items = subscription.getItems().getData();
      if (items == null || items.isEmpty()) {
        return Optional.empty();
      }
      Long epochSec = items.get(0).getCurrentPeriodEnd();
      return epochSec != null ? Optional.of(Instant.ofEpochSecond(epochSec)) : Optional.empty();
    } catch (Exception e) {
      log.warn(
          "StripeReconciliationJob: could not extract current_period_end for sub {}: {}",
          subscription.getId(),
          e.getMessage());
      return Optional.empty();
    }
  }

  private boolean isPeriodDrifted(Instant tenantPeriodEnd, Instant resolvedPeriodEnd) {
    if (resolvedPeriodEnd == null) {
      return false;
    }
    if (tenantPeriodEnd == null) {
      return true;
    }
    return Math.abs(resolvedPeriodEnd.getEpochSecond() - tenantPeriodEnd.getEpochSecond())
        > PERIOD_DRIFT_THRESHOLD_SECONDS;
  }
}
