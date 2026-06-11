package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.net.Webhook;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for POST /api/v1/billing/webhook (TASK-063, PRD §17.3).
 *
 * <p>Stripe SDK calls are intercepted with {@link MockedStatic} so the tests run without a live
 * Stripe account. MockMvc is synchronous — the static mocks are active on the same thread as the
 * request handler, so interception works correctly.
 *
 * <ul>
 *   <li>Step 1: Invalid signature → 400; no DB changes.
 *   <li>Step 2: {@code customer.subscription.updated} → tenant plan updated + {@code
 *       SubscriptionUpdated} outbox event published.
 *   <li>Step 3: Idempotency — same event POSTed twice → only one {@code stripe_webhook_events} row.
 *   <li>Step 4: {@code invoice.payment_failed} → {@code payment_failed_at} set + {@code
 *       delayed_jobs} row inserted.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class BillingWebhookIT {

  private static final String FAKE_CUSTOMER_ID = "cus_webhook_test";

  private static final String FAKE_SUBSCRIPTION_ID = "sub_webhook_test";

  private static final String WEBHOOK_URL = "/api/v1/billing/webhook";

  private static final String DUMMY_SIG = "t=1234,v1=dummy_sig_for_tests";

  private static final String DUMMY_PAYLOAD = "{\"id\":\"evt_test\"}";

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private EntityManager entityManager;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantService.provisionWithAdminUser("Webhook Corp", "webhook-admin@corp.com");
    tenant.setStripeCustomerId(FAKE_CUSTOMER_ID);
    tenantRepository.saveAndFlush(tenant);
  }

  /** Step 1 (step3 in task): Invalid/tampered signature → 400, nothing written to DB. */
  @Test
  void step1_invalidSignature_returns400() throws Exception {
    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock
          .when(() -> Webhook.constructEvent(any(), any(), any()))
          .thenThrow(new SignatureVerificationException("Invalid signature", DUMMY_SIG));

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isBadRequest());

      Integer count =
          jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stripe_webhook_events", Integer.class);
      assertThat(count).isZero();
    }
  }

  /**
   * Step 2 (step1 in task): {@code customer.subscription.updated} → tenant plan set to TEAM, {@code
   * stripe_subscription_id} updated, and {@code SubscriptionUpdated} event in outbox.
   */
  @Test
  void step2_subscriptionUpdated_updatesTenantAndPublishesOutboxEvent() throws Exception {
    com.stripe.model.Event mockEvent = buildSubscriptionUpdatedEvent("evt_sub_updated_001", "team");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.TEAM);
    assertThat(updated.getStripeSubscriptionId()).isEqualTo(FAKE_SUBSCRIPTION_ID);
    assertThat(updated.getCurrentPeriodEnd()).isNotNull();

    Integer eventCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stripe_webhook_events WHERE id = 'evt_sub_updated_001'",
            Integer.class);
    assertThat(eventCount).isEqualTo(1);

    Integer outboxCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'SubscriptionUpdated'"
                + " AND aggregate_type = 'Tenant' AND aggregate_id = ?",
            Integer.class,
            tenant.getId());
    assertThat(outboxCount).isEqualTo(1);
  }

  /** Step 3: Same event POSTed twice → idempotency: only one stripe_webhook_events row. */
  @Test
  void step3_idempotency_sameEventTwice_onlyOneRow() throws Exception {
    com.stripe.model.Event mockEvent = buildSubscriptionUpdatedEvent("evt_idempotent_001", "team");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stripe_webhook_events WHERE id = 'evt_idempotent_001'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  /**
   * Step 4 (step2 in task): {@code invoice.payment_failed} → {@code payment_failed_at} set on
   * tenant and a {@code downgrade_to_free} delayed job scheduled ~7 days out.
   */
  @Test
  void step4_paymentFailed_setsFlagAndSchedulesDowngradeJob() throws Exception {
    com.stripe.model.Event mockEvent = buildPaymentFailedEvent("evt_payment_failed_001");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPaymentFailedAt()).isNotNull();
    assertThat(updated.getPaymentFailedAt()).isBeforeOrEqualTo(Instant.now());

    Integer jobCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM delayed_jobs WHERE kind = 'downgrade_to_free'"
                + " AND tenant_id = ?",
            Integer.class,
            tenant.getId());
    assertThat(jobCount).isEqualTo(1);
  }

  // =========================================================================
  // Additional coverage tests
  // =========================================================================

  /**
   * Step 5: {@code customer.subscription.deleted} → tenant plan reset to FREE and subscription ID
   * cleared.
   */
  @Test
  void step5_subscriptionDeleted_downgradesTenantToFree() throws Exception {
    tenant.setPlan(Plan.TEAM);
    tenant.setStripeSubscriptionId(FAKE_SUBSCRIPTION_ID);
    tenantRepository.saveAndFlush(tenant);

    com.stripe.model.Event mockEvent = buildSubscriptionDeletedEvent("evt_sub_deleted_001");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();

    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
    assertThat(updated.getStripeSubscriptionId()).isNull();
  }

  /** Step 6: Unrecognised event type → 200 OK, no tenant mutation (default switch branch). */
  @Test
  void step6_unknownEventType_isIgnoredWithOkResponse() throws Exception {
    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_unknown_001", "some.future.event.type", mock(Invoice.class));

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    Tenant unchanged = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(unchanged.getPlan()).isEqualTo(Plan.FREE);
  }

  /** Step 7: Deserializer returns empty Optional → orElseThrow fires → 500 internal error. */
  @Test
  void step7_emptyDeserializerObject_returns500() throws Exception {
    EventDataObjectDeserializer emptyDeserializer = mock(EventDataObjectDeserializer.class);
    when(emptyDeserializer.getObject()).thenReturn(Optional.empty());

    com.stripe.model.Event mockEvent = mock(com.stripe.model.Event.class);
    when(mockEvent.getId()).thenReturn("evt_empty_obj_001");
    when(mockEvent.getType()).thenReturn("customer.subscription.updated");
    when(mockEvent.getDataObjectDeserializer()).thenReturn(emptyDeserializer);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isInternalServerError());
    }
  }

  /** Step 8: Event references a customer ID that matches no tenant → 500 internal error. */
  @Test
  void step8_missingTenantForCustomerId_returns500() throws Exception {
    com.stripe.model.Event mockEvent =
        buildSubscriptionUpdatedEventWithCustomer("evt_no_tenant_001", "cus_does_not_exist");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isInternalServerError());
    }
  }

  /**
   * Step 9: {@code customer.subscription.updated} with empty item list → extractPlan returns FREE
   * and extractCurrentPeriodEnd returns empty (both empty-items branches covered).
   */
  @Test
  void step9_subscriptionUpdated_withEmptyItems_planBecomeFree() throws Exception {
    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of());

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenReturn(mockItems);

    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_empty_items_001", "customer.subscription.updated", mockSub);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();
    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
  }

  /** Step 10: First subscription item has a null price → extractPlan returns FREE. */
  @Test
  void step10_subscriptionUpdated_withPriceNull_planBecomeFree() throws Exception {
    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(null);
    when(mockItem.getCurrentPeriodEnd())
        .thenReturn(Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond());

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenReturn(mockItems);

    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_price_null_001", "customer.subscription.updated", mockSub);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();
    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
  }

  /**
   * Step 11: lookup_key is unrecognised, but {@code teamhub_plan_key} metadata carries a valid plan
   * → plan resolved from metadata.
   */
  @Test
  void step11_subscriptionUpdated_withMetadataFallback_planResolvedFromMeta() throws Exception {
    Price mockPrice = mock(Price.class);
    when(mockPrice.getLookupKey()).thenReturn("teamhub_unrecognised_plan");
    when(mockPrice.getMetadata()).thenReturn(Map.of("teamhub_plan_key", "TEAM"));

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    when(mockItem.getCurrentPeriodEnd())
        .thenReturn(Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond());

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenReturn(mockItems);

    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_meta_ok_001", "customer.subscription.updated", mockSub);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();
    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.TEAM);
  }

  /**
   * Step 12: lookup_key unrecognised and metadata plan key is also invalid → both IAE catch blocks
   * hit, plan falls back to FREE.
   */
  @Test
  void step12_subscriptionUpdated_withBadMetadata_planFallsToFree() throws Exception {
    Price mockPrice = mock(Price.class);
    when(mockPrice.getLookupKey()).thenReturn("teamhub_unrecognised_plan");
    when(mockPrice.getMetadata()).thenReturn(Map.of("teamhub_plan_key", "GARBAGE_PLAN_NAME"));

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    when(mockItem.getCurrentPeriodEnd())
        .thenReturn(Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond());

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenReturn(mockItems);

    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_meta_bad_001", "customer.subscription.updated", mockSub);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();
    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
  }

  /**
   * Step 13: {@code subscription.getItems()} throws a runtime exception → both extractPlan and
   * extractCurrentPeriodEnd exception-catch blocks are reached; plan defaults to FREE.
   */
  @Test
  void step13_subscriptionUpdated_withGetItemsException_planFallsToFree() throws Exception {
    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenThrow(new RuntimeException("simulated SDK error"));

    com.stripe.model.Event mockEvent =
        wrapInEvent("evt_items_ex_001", "customer.subscription.updated", mockSub);

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(mockEvent);

      mockMvc
          .perform(
              post(WEBHOOK_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Stripe-Signature", DUMMY_SIG)
                  .content(DUMMY_PAYLOAD))
          .andExpect(status().isOk());
    }

    entityManager.flush();
    Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(updated.getPlan()).isEqualTo(Plan.FREE);
  }

  // =========================================================================
  // Mock event builders
  // =========================================================================

  private com.stripe.model.Event buildSubscriptionDeletedEvent(String eventId) {
    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    return wrapInEvent(eventId, "customer.subscription.deleted", mockSub);
  }

  private com.stripe.model.Event buildSubscriptionUpdatedEventWithCustomer(
      String eventId, String customerId) {
    Price mockPrice = mock(Price.class);
    when(mockPrice.getLookupKey()).thenReturn("teamhub_team");

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    when(mockItem.getCurrentPeriodEnd())
        .thenReturn(Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond());

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(customerId);
    when(mockSub.getItems()).thenReturn(mockItems);

    return wrapInEvent(eventId, "customer.subscription.updated", mockSub);
  }

  private com.stripe.model.Event buildSubscriptionUpdatedEvent(String eventId, String planKey) {
    Price mockPrice = mock(Price.class);
    when(mockPrice.getLookupKey()).thenReturn("teamhub_" + planKey);

    SubscriptionItem mockItem = mock(SubscriptionItem.class);
    when(mockItem.getPrice()).thenReturn(mockPrice);
    // In API 2026-04-22.dahlia, current_period_end lives on SubscriptionItem.
    when(mockItem.getCurrentPeriodEnd())
        .thenReturn(Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond());

    SubscriptionItemCollection mockItems = mock(SubscriptionItemCollection.class);
    when(mockItems.getData()).thenReturn(List.of(mockItem));

    Subscription mockSub = mock(Subscription.class);
    when(mockSub.getId()).thenReturn(FAKE_SUBSCRIPTION_ID);
    when(mockSub.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    when(mockSub.getItems()).thenReturn(mockItems);

    return wrapInEvent(eventId, "customer.subscription.updated", mockSub);
  }

  private com.stripe.model.Event buildPaymentFailedEvent(String eventId) {
    Invoice mockInvoice = mock(Invoice.class);
    when(mockInvoice.getCustomer()).thenReturn(FAKE_CUSTOMER_ID);
    return wrapInEvent(eventId, "invoice.payment_failed", mockInvoice);
  }

  private com.stripe.model.Event wrapInEvent(
      String eventId, String eventType, com.stripe.model.StripeObject stripeObject) {
    EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);
    when(mockDeserializer.getObject()).thenReturn(Optional.of(stripeObject));

    com.stripe.model.Event mockEvent = mock(com.stripe.model.Event.class);
    when(mockEvent.getId()).thenReturn(eventId);
    when(mockEvent.getType()).thenReturn(eventType);
    when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);
    return mockEvent;
  }
}
