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
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(TestcontainersConfiguration.class)
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
    tenant = tenantService.provision("Webhook Corp", "webhook-admin@corp.com");
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
  // Mock event builders
  // =========================================================================

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
