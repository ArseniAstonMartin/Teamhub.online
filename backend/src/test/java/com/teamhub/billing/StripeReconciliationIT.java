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
  // Additional coverage tests for ReconciliationBatchRunner branches
  // ---------------------------------------------------------------------------

  /**
   * Step 4: Subscription price ID is not in the configured set → extractPlan returns empty → WARN
   * logged and tenant skipped (unknown-price-ID path).
   */
  @Test
  void step4_unknownPriceId_warnAndSkip() throws Exception {
    Tenant tenant = new Tenant("Unknown Price Corp");
    tenant.setPlan(Plan.STARTER);
    tenant.setStripeCustomerId("cus_unknown_price");
    tenant.setStripeSubscriptionId("sub_unknown_price");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription mockSub =
        buildMockSubscription("sub_unknown_price", "price_completely_unknown", periodEnd);

    when(subscriptionFetcher.fetch(eq("sub_unknown_price"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan())
        .as("Plan must not change when price ID is unrecognised")
        .isEqualTo(Plan.STARTER);
  }

  /**
   * Step 5: extractPlan gets empty items list → resolves to empty Optional → tenant skipped; also
   * covers extractCurrentPeriodEnd empty-items branch via resolvePeriodEnd=null →
   * isPeriodDrifted(null) → return false.
   */
  @Test
  void step5_emptyItems_tenantSkipped() throws Exception {
    Tenant tenant = new Tenant("Empty Items Corp");
    tenant.setPlan(Plan.TEAM);
    tenant.setStripeCustomerId("cus_empty_items");
    tenant.setStripeSubscriptionId("sub_empty_items");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    Subscription mockSub = buildMockSubscriptionWithEmptyItems("sub_empty_items");

    when(subscriptionFetcher.fetch(eq("sub_empty_items"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.TEAM);
  }

  /** Step 6: Price object is null → extractPlan returns empty → tenant skipped. */
  @Test
  void step6_priceNull_tenantSkipped() throws Exception {
    Tenant tenant = new Tenant("Null Price Corp");
    tenant.setPlan(Plan.TEAM);
    tenant.setStripeCustomerId("cus_null_price");
    tenant.setStripeSubscriptionId("sub_null_price");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(null);

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn("sub_null_price");
    when(mockSub.getItems()).thenReturn(mockItems);

    when(subscriptionFetcher.fetch(eq("sub_null_price"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.TEAM);
  }

  /**
   * Step 7: Price ID is null → resolvePlanFromPriceId null-branch → extractPlan returns empty →
   * tenant skipped.
   */
  @Test
  void step7_nullPriceId_tenantSkipped() throws Exception {
    Tenant tenant = new Tenant("Null PriceId Corp");
    tenant.setPlan(Plan.TEAM);
    tenant.setStripeCustomerId("cus_null_priceid");
    tenant.setStripeSubscriptionId("sub_null_priceid");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    Price mockPrice = mock(Price.class);
    when(mockPrice.getId()).thenReturn(null);

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn("sub_null_priceid");
    when(mockSub.getItems()).thenReturn(mockItems);

    when(subscriptionFetcher.fetch(eq("sub_null_priceid"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.TEAM);
  }

  /**
   * Step 8: subscription.getItems() throws during extractPlan → exception catch branch hit →
   * extractPlan returns empty → tenant skipped.
   */
  @Test
  void step8_extractPlanException_tenantSkipped() throws Exception {
    Tenant tenant = new Tenant("Extract Plan Exception Corp");
    tenant.setPlan(Plan.TEAM);
    tenant.setStripeCustomerId("cus_plan_ex");
    tenant.setStripeSubscriptionId("sub_plan_ex");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn("sub_plan_ex");
    when(mockSub.getItems()).thenThrow(new RuntimeException("SDK error during extractPlan"));

    when(subscriptionFetcher.fetch(eq("sub_plan_ex"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.TEAM);
  }

  /**
   * Step 9: extractPlan succeeds but subscription.getItems() throws on the second call (inside
   * extractCurrentPeriodEnd) → exception catch branch in extractCurrentPeriodEnd hit → period end
   * treated as absent, but plan drift is still corrected.
   */
  @Test
  void step9_extractCurrentPeriodEndException_planCorrectedPeriodEndUnchanged() throws Exception {
    Tenant tenant = new Tenant("Period End Exception Corp");
    tenant.setPlan(Plan.FREE);
    tenant.setStripeCustomerId("cus_period_ex");
    tenant.setStripeSubscriptionId("sub_period_ex");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    Price mockPrice = mock(Price.class);
    when(mockPrice.getId()).thenReturn(PRICE_STARTER);

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData())
        .thenReturn(List.of(mockItem)) // first call: extractPlan succeeds
        .thenThrow(new RuntimeException("SDK error during extractCurrentPeriodEnd"));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn("sub_period_ex");
    when(mockSub.getItems()).thenReturn(mockItems);

    when(subscriptionFetcher.fetch(eq("sub_period_ex"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan())
        .as("Plan drift must be corrected even when period end extraction throws")
        .isEqualTo(Plan.STARTER);
  }

  /** Step 10: Subscription has FREE price ID → tenant corrected to FREE plan. */
  @Test
  void step10_freePriceId_planCorrectedToFree() throws Exception {
    Tenant tenant = new Tenant("Free Price Corp");
    tenant.setPlan(Plan.STARTER);
    tenant.setStripeCustomerId("cus_free_recon");
    tenant.setStripeSubscriptionId("sub_free_recon");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription mockSub = buildMockSubscription("sub_free_recon", "price_test_free", periodEnd);

    when(subscriptionFetcher.fetch(eq("sub_free_recon"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
  }

  /** Step 11: Subscription has TEAM price ID → tenant corrected to TEAM plan. */
  @Test
  void step11_teamPriceId_planCorrectedToTeam() throws Exception {
    Tenant tenant = new Tenant("Team Price Corp");
    tenant.setPlan(Plan.FREE);
    tenant.setStripeCustomerId("cus_team_recon");
    tenant.setStripeSubscriptionId("sub_team_recon");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription mockSub = buildMockSubscription("sub_team_recon", "price_test_team", periodEnd);

    when(subscriptionFetcher.fetch(eq("sub_team_recon"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.TEAM);
  }

  /** Step 12: Subscription has ENTERPRISE price ID → tenant corrected to ENTERPRISE plan. */
  @Test
  void step12_enterprisePriceId_planCorrectedToEnterprise() throws Exception {
    Tenant tenant = new Tenant("Enterprise Price Corp");
    tenant.setPlan(Plan.FREE);
    tenant.setStripeCustomerId("cus_enterprise_recon");
    tenant.setStripeSubscriptionId("sub_enterprise_recon");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    long periodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();
    Subscription mockSub =
        buildMockSubscription("sub_enterprise_recon", "price_test_enterprise", periodEnd);

    when(subscriptionFetcher.fetch(eq("sub_enterprise_recon"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.ENTERPRISE);
  }

  /**
   * Step 13: resolvedPeriodEnd is null (items empty in extractCurrentPeriodEnd) and plan matches →
   * isPeriodDrifted(tenantPeriodEnd, null) returns false → no update.
   */
  @Test
  void step13_resolvedPeriodEndNull_noDrift_noUpdate() throws Exception {
    long existingPeriodEnd = Instant.now().plusSeconds(30 * 24 * 3600L).getEpochSecond();

    Tenant tenant = new Tenant("No Period End Corp");
    tenant.setPlan(Plan.STARTER);
    tenant.setCurrentPeriodEnd(Instant.ofEpochSecond(existingPeriodEnd));
    tenant.setStripeCustomerId("cus_no_period");
    tenant.setStripeSubscriptionId("sub_no_period");
    tenantRepository.save(tenant);
    createdTenantIds.add(tenant.getId());

    // extractPlan returns STARTER (plan matches) but extractCurrentPeriodEnd returns empty →
    // resolvedPeriodEnd = null → isPeriodDrifted(existing, null) = false
    Price mockPrice = mock(Price.class);
    when(mockPrice.getId()).thenReturn(PRICE_STARTER);

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    when(mockItem.getCurrentPeriodEnd()).thenReturn(null);

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn("sub_no_period");
    when(mockSub.getItems()).thenReturn(mockItems);

    when(subscriptionFetcher.fetch(eq("sub_no_period"), any(RequestOptions.class)))
        .thenReturn(mockSub);

    reconciliationJob.runReconciliation();

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.STARTER);
    assertThat(unchanged.getCurrentPeriodEnd().getEpochSecond()).isEqualTo(existingPeriodEnd);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Subscription buildMockSubscriptionWithEmptyItems(String subscriptionId) {
    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of());

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(subscriptionId);
    when(mockSub.getItems()).thenReturn(mockItems);
    return mockSub;
  }

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
