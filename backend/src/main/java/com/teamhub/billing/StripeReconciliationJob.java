package com.teamhub.billing;

import com.stripe.net.RequestOptions;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily Stripe subscription reconciliation job (TASK-066, PRD §28).
 *
 * <p>Runs once per day at 03:00 UTC and iterates over every tenant that has a Stripe subscription
 * ID. For each tenant it fetches the live subscription from the Stripe API and corrects any drift
 * in {@code plan} or {@code current_period_end} that may have been missed by the webhook handler.
 *
 * <p>The job uses {@code @SchedulerLock} to guarantee single-leader execution across all JVM nodes.
 * Tenants are processed in parallel batches of {@value #BATCH_SIZE} to meet the PRD §24.2 target of
 * 200 tenants in under 5 minutes.
 *
 * <p>Any {@code StripeException} for an individual tenant is caught and logged; the job continues
 * with the remaining tenants.
 */
@Component
public class StripeReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(StripeReconciliationJob.class);

  private static final String STRIPE_API_VERSION = "2026-04-22.dahlia";

  static final int BATCH_SIZE = 10;

  private final TenantRepository tenantRepository;

  private final ReconciliationBatchRunner batchRunner;

  public StripeReconciliationJob(
      TenantRepository tenantRepository, ReconciliationBatchRunner batchRunner) {
    this.tenantRepository = tenantRepository;
    this.batchRunner = batchRunner;
  }

  /**
   * Entry point for the Spring scheduler.
   *
   * <p>{@code @SchedulerLock} ensures at most one node runs this job at a time. {@code
   * lockAtMostFor} is set to 4 minutes so a crashed node releases the lock before the next
   * scheduled run; {@code lockAtLeastFor} prevents spurious re-runs during fast test cycles.
   */
  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  @SchedulerLock(name = "stripe-reconciliation", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
  public void schedule() {
    runReconciliation();
  }

  /**
   * Core reconciliation logic — exposed at package visibility for direct invocation from
   * integration tests.
   */
  void runReconciliation() {
    long start = System.currentTimeMillis();
    log.info("StripeReconciliationJob: starting");

    List<Tenant> tenants = tenantRepository.findAllWithActiveSubscription();
    log.info(
        "StripeReconciliationJob: found {} tenant(s) with active subscriptions", tenants.size());

    RequestOptions requestOptions =
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(
                RequestOptions.builder(), STRIPE_API_VERSION)
            .build();

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < tenants.size(); i += BATCH_SIZE) {
      List<Tenant> batch = tenants.subList(i, Math.min(i + BATCH_SIZE, tenants.size()));
      futures.add(batchRunner.processBatch(batch, requestOptions));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    long elapsed = System.currentTimeMillis() - start;
    log.info("StripeReconciliationJob: finished in {}ms", elapsed);
  }
}
