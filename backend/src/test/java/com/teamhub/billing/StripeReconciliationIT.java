package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.net.RequestOptions;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for {@link StripeReconciliationJob} (TASK-066, PRD §28).
 *
 * <p>{@link SubscriptionFetcher} is replaced by a {@code @MockitoBean} so no live Stripe account is
 * required. Unlike {@code MockedStatic}, a {@code @MockitoBean} is a thread-safe Spring bean and
 * therefore works correctly when called from the {@code @Async} batch executor threads.
 *
 * <ul>
 *   <li><b>Step 1</b>: Tenant with wrong plan — reconciliation corrects it and emits a {@code
 *       SubscriptionDrift} WARN log.
 *   <li><b>Step 2 (performance)</b>: 200 tenants with no drift — {@code runReconciliation()}
 *       completes in under 120 seconds (PRD §24.2).
 *   <li><b>Step 3</b>: {@code StripeException} on one tenant does not abort the job; the other
 *       tenant is still processed correctly.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StripeReconciliationIT {

  private static final String SUB_DRIFT = "sub_drift_test";

  private static final String PRICE_STARTER = "price_test_starter";

  @MockitoBean private SubscriptionFetcher subscriptionFetcher;

  @Autowired private StripeReconciliationJob reconciliationJob;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<java.util.UUID> createdTenantIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    createdTenantIds.clear();
  }

  @AfterEach
  void cleanUp() {
    createdTenantIds.forEach(id -> jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", id));
  }

  // ---------------------------------------------------------------------------
  // Step 1: drift correction + SubscriptionDrift log line
  // ---------------------------------------------------------------------------

  /**
   * Step 1: Insert a tenant whose local plan is {@code FREE} but the Stripe subscription holds
   * {@code STARTER}. Run reconciliation and verify the plan is corrected, {@code currentPeriodEnd}
   * is set, and a WARN log containing {@code SubscriptionDrift} is emitted.
   */
  @Test
  void step1_driftDetected_planCorrectedAndWarnLogged() throws Exception {
    Tenant tenant = new Tenant("Drift Corp");
    tenant.setPlan(Plan.FREE);
    tenant.setStripeCustomerId("cus_drift_test");
    tenant.setStripeSubscriptionId(SUB_DRIFT);
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    long futureEpoch = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription mockSub = buildMockSubscription(SUB_DRIFT, PRICE_STARTER, futureEpoch);

    when(subscriptionFetcher.fetch(eq(SUB_DRIFT), any(RequestOptions.class))).thenReturn(mockSub);

    ch.qos.logback.classic.Logger batchLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ReconciliationBatchRunner.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    batchLogger.addAppender(listAppender);
    try {
      reconciliationJob.runReconciliation();
    } finally {
      batchLogger.detachAppender(listAppender);
    }

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.STARTER);
    assertThat(updated.getCurrentPeriodEnd()).isNotNull();
    assertThat(updated.getCurrentPeriodEnd().getEpochSecond()).isEqualTo(futureEpoch);

    boolean warnFound =
        listAppender.list.stream()
            .anyMatch(
                e ->
                    e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("SubscriptionDrift"));
    assertThat(warnFound).as("Expected a WARN log containing 'SubscriptionDrift'").isTrue();
  }

  // ---------------------------------------------------------------------------
  // Step 2: performance — 200 tenants, no drift, finishes in < 120 s
  // ---------------------------------------------------------------------------

  /**
   * Step 2 (performance): Bulk-insert 200 tenants that already have the correct plan and period.
   * Run reconciliation and assert it completes in under 120 seconds (PRD §24.2 target).
   */
  @Test
  void step2_200Tenants_completesWithinTimeLimit() throws Exception {
    int count = 200;
    long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();

    List<Object[]> batchArgs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      java.util.UUID id = java.util.UUID.randomUUID();
      createdTenantIds.add(id);
      batchArgs.add(
          new Object[] {
            id.toString(), "PerfTenant " + i, "cus_perf_" + id, "sub_perf_" + id, periodEnd
          });
    }

    jdbcTemplate.batchUpdate(
        "INSERT INTO tenants"
            + " (id, name, plan, created_at, stripe_customer_id, stripe_subscription_id,"
            + "  current_period_end, close_jobs_enabled)"
            + " VALUES (CAST(? AS uuid), ?, 'STARTER', now(), ?, ?, to_timestamp(?), false)",
        batchArgs);

    Subscription mockSub = buildMockSubscription("sub_perf", PRICE_STARTER, periodEnd);
    when(subscriptionFetcher.fetch(any(String.class), any(RequestOptions.class)))
        .thenReturn(mockSub);

    assertTimeoutPreemptively(
        Duration.ofSeconds(120),
        () -> reconciliationJob.runReconciliation(),
        "reconciliation of 200 tenants must complete within 120 seconds");
  }

  // ---------------------------------------------------------------------------
  // Step 3: StripeException on one tenant does not abort the job
  // ---------------------------------------------------------------------------

  /**
   * Step 3: One tenant's subscription retrieval throws a {@code StripeException}; the other tenant
   * is still processed correctly and a WARN log is emitted for the failing subscription.
   */
  @Test
  void step3_stripeExceptionOnOneTenant_doesNotAbortOthers() throws Exception {
    Tenant failingTenant = new Tenant("Failing Corp");
    failingTenant.setPlan(Plan.FREE);
    failingTenant.setStripeCustomerId("cus_fail");
    failingTenant.setStripeSubscriptionId("sub_fail");
    tenantRepository.save(failingTenant);
    createdTenantIds.add(failingTenant.getId());

    Tenant okTenant = new Tenant("OK Corp");
    okTenant.setPlan(Plan.FREE);
    okTenant.setStripeCustomerId("cus_ok");
    okTenant.setStripeSubscriptionId("sub_ok");
    tenantRepository.save(okTenant);
    createdTenantIds.add(okTenant.getId());

    long futureEpoch = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription okMockSub = buildMockSubscription("sub_ok", PRICE_STARTER, futureEpoch);

    when(subscriptionFetcher.fetch(eq("sub_fail"), any(RequestOptions.class)))
        .thenThrow(new ApiConnectionException("network error"));
    when(subscriptionFetcher.fetch(eq("sub_ok"), any(RequestOptions.class))).thenReturn(okMockSub);

    ch.qos.logback.classic.Logger batchLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ReconciliationBatchRunner.class);
    ListAppender<ILoggingEvent> warnAppender = new ListAppender<>();
    warnAppender.start();
    batchLogger.addAppender(warnAppender);
    try {
      reconciliationJob.runReconciliation();
    } finally {
      batchLogger.detachAppender(warnAppender);
    }

    Tenant updatedOk = tenantRepository.findById(okTenant.getId()).orElseThrow();
    assertThat(updatedOk.getPlan())
        .as("OK tenant should be updated to STARTER")
        .isEqualTo(Plan.STARTER);

    Tenant updatedFailing = tenantRepository.findById(failingTenant.getId()).orElseThrow();
    assertThat(updatedFailing.getPlan())
        .as("Failing tenant plan should remain unchanged at FREE")
        .isEqualTo(Plan.FREE);

    boolean warnLogged =
        warnAppender.list.stream()
            .anyMatch(
                e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("sub_fail"));
    assertThat(warnLogged).as("Expected a WARN log about the failing subscription").isTrue();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Subscription buildMockSubscription(
      String subscriptionId, String priceId, long periodEndEpoch) {
    Price mockPrice = mock(Price.class);
    when(mockPrice.getId()).thenReturn(priceId);

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    when(mockItem.getCurrentPeriodEnd()).thenReturn(periodEndEpoch);

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(subscriptionId);
    when(mockSub.getItems()).thenReturn(mockItems);

    return mockSub;
  }
}
