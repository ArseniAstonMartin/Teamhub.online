package com.teamhub.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.Webhook;
import com.teamhub.eventing.DelayedJobDispatcher;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.SubscriptionUpdated;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies and routes incoming Stripe webhook events (TASK-063, PRD §17.3).
 *
 * <p>Every call to {@link #handleEvent} first verifies the HMAC signature using {@code
 * STRIPE_WEBHOOK_SECRET}, then checks idempotency against the {@code stripe_webhook_events} table
 * (one row per Stripe event ID), then dispatches to the per-event-type handler inside a single
 * database transaction.
 *
 * <p>Handled event types:
 *
 * <ul>
 *   <li>{@code customer.subscription.updated} — updates {@code plan}, {@code
 *       stripe_subscription_id}, {@code current_period_end}; publishes {@code SubscriptionUpdated}.
 *   <li>{@code customer.subscription.deleted} — resets plan to {@code FREE}; publishes {@code
 *       SubscriptionUpdated}.
 *   <li>{@code invoice.payment_failed} — sets {@code payment_failed_at}; schedules a {@code
 *       downgrade_to_free} delayed job for now+7d; publishes {@code SubscriptionUpdated}.
 * </ul>
 */
@Service
public class WebhookHandlerService {

  private static final Logger log = LoggerFactory.getLogger(WebhookHandlerService.class);

  private static final String KIND_DOWNGRADE_TO_FREE = "downgrade_to_free";

  private final StripePriceProperties properties;

  private final TenantRepository tenantRepository;

  private final StripeWebhookEventRepository webhookEventRepository;

  private final OutboxPublisher outboxPublisher;

  private final DelayedJobDispatcher delayedJobDispatcher;

  public WebhookHandlerService(
      StripePriceProperties properties,
      TenantRepository tenantRepository,
      StripeWebhookEventRepository webhookEventRepository,
      OutboxPublisher outboxPublisher,
      DelayedJobDispatcher delayedJobDispatcher) {
    this.properties = properties;
    this.tenantRepository = tenantRepository;
    this.webhookEventRepository = webhookEventRepository;
    this.outboxPublisher = outboxPublisher;
    this.delayedJobDispatcher = delayedJobDispatcher;
  }

  /**
   * Entry point called by {@link BillingController#handleWebhook}.
   *
   * <p>Signature verification is performed first (outside any transaction). The transactional state
   * mutation only proceeds when the signature is valid and the event has not been processed before.
   *
   * @throws SignatureVerificationException if {@code Stripe-Signature} does not match the webhook
   *     secret — the controller maps this to HTTP 400
   */
  @Transactional
  public void handleEvent(String payload, String sigHeader) throws SignatureVerificationException {
    com.stripe.model.Event event =
        Webhook.constructEvent(payload, sigHeader, properties.webhookSecret());

    if (webhookEventRepository.existsById(event.getId())) {
      log.info("Stripe event {} already processed — skipping (idempotency)", event.getId());
      return;
    }

    webhookEventRepository.save(
        new StripeWebhookEvent(event.getId(), event.getType(), Instant.now()));

    StripeObject stripeObject =
        event
            .getDataObjectDeserializer()
            .getObject()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot deserialize Stripe event object for event " + event.getId()));

    switch (event.getType()) {
      case "customer.subscription.updated" ->
          handleSubscriptionUpdated((Subscription) stripeObject);
      case "customer.subscription.deleted" ->
          handleSubscriptionDeleted((Subscription) stripeObject);
      case "invoice.payment_failed" -> handlePaymentFailed((Invoice) stripeObject);
      default -> log.debug("Unhandled Stripe event type: {}", event.getType());
    }
  }

  // =========================================================================
  // Per-event-type handlers
  // =========================================================================

  private void handleSubscriptionUpdated(Subscription subscription) {
    Tenant tenant = requireTenantByCustomerId(subscription.getCustomer());
    Plan oldPlan = tenant.getPlan();
    Plan newPlan = extractPlan(subscription);

    tenant.setPlan(newPlan);
    tenant.setStripeSubscriptionId(subscription.getId());
    // In API 2026-04-22.dahlia, current_period_end lives on SubscriptionItem, not Subscription.
    extractCurrentPeriodEnd(subscription).ifPresent(tenant::setCurrentPeriodEnd);
    tenantRepository.save(tenant);

    publishSubscriptionUpdated(tenant, subscription.getId(), oldPlan, newPlan);
    log.info(
        "Tenant {} subscription updated: plan {} → {}, sub {}",
        tenant.getId(),
        oldPlan,
        newPlan,
        subscription.getId());
  }

  private void handleSubscriptionDeleted(Subscription subscription) {
    Tenant tenant = requireTenantByCustomerId(subscription.getCustomer());
    Plan oldPlan = tenant.getPlan();

    tenant.setPlan(Plan.FREE);
    tenant.setStripeSubscriptionId(null);
    tenantRepository.save(tenant);

    publishSubscriptionUpdated(tenant, subscription.getId(), oldPlan, Plan.FREE);
    log.info("Tenant {} subscription deleted — downgraded to FREE", tenant.getId());
  }

  private void handlePaymentFailed(Invoice invoice) {
    Tenant tenant = requireTenantByCustomerId(invoice.getCustomer());

    tenant.setPaymentFailedAt(Instant.now());
    tenantRepository.save(tenant);

    delayedJobDispatcher.schedule(
        tenant.getId(),
        KIND_DOWNGRADE_TO_FREE,
        "{\"reason\":\"invoice.payment_failed\"}",
        Instant.now().plusSeconds(7L * 24 * 60 * 60));

    publishSubscriptionUpdated(tenant, null, tenant.getPlan(), tenant.getPlan());
    log.info(
        "Tenant {} payment failed — flagged and downgrade_to_free job scheduled for +7d",
        tenant.getId());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private Tenant requireTenantByCustomerId(String customerId) {
    return tenantRepository
        .findByStripeCustomerId(customerId)
        .orElseThrow(
            () -> new IllegalStateException("No tenant found for Stripe customer: " + customerId));
  }

  private void publishSubscriptionUpdated(
      Tenant tenant, String subscriptionId, Plan oldPlan, Plan newPlan) {
    SubscriptionUpdated payload =
        new SubscriptionUpdated(
            UUID.randomUUID(),
            tenant.getId(),
            subscriptionId,
            oldPlan == null ? null : oldPlan.name(),
            newPlan == null ? null : newPlan.name(),
            Instant.now());

    TenantContext.set(tenant.getId());
    try {
      outboxPublisher.publish("SubscriptionUpdated", "Tenant", tenant.getId(), payload);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Returns the billing period end from the first subscription item.
   *
   * <p>In Stripe API 2026-04-22.dahlia, {@code current_period_end} lives on {@link
   * SubscriptionItem}, not on the top-level {@link Subscription} object.
   */
  private Optional<Instant> extractCurrentPeriodEnd(Subscription subscription) {
    try {
      List<SubscriptionItem> items = subscription.getItems().getData();
      if (items == null || items.isEmpty()) {
        return Optional.empty();
      }
      Long epochSec = items.get(0).getCurrentPeriodEnd();
      return epochSec != null ? Optional.of(Instant.ofEpochSecond(epochSec)) : Optional.empty();
    } catch (Exception e) {
      log.warn(
          "Could not extract current_period_end from subscription {}: {}",
          subscription.getId(),
          e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Derives the {@link Plan} from the first subscription item's price {@code lookup_key}.
   *
   * <p>Expected lookup_key format: {@code "teamhub_<plan>"} (e.g. {@code "teamhub_team"}). Falls
   * back to {@link Plan#FREE} if the key is absent or unrecognised.
   */
  private Plan extractPlan(Subscription subscription) {
    try {
      List<com.stripe.model.SubscriptionItem> items = subscription.getItems().getData();
      if (items == null || items.isEmpty()) {
        return Plan.FREE;
      }
      com.stripe.model.Price price = items.get(0).getPrice();
      if (price == null) {
        return Plan.FREE;
      }
      String lookupKey = price.getLookupKey();
      if (lookupKey != null && !lookupKey.isBlank()) {
        String planKey = lookupKey.toLowerCase().replace("teamhub_", "");
        try {
          return Plan.valueOf(planKey.toUpperCase());
        } catch (IllegalArgumentException ignored) {
          // lookup_key does not match a known plan — fall through to metadata
        }
      }
      Map<String, String> metadata = price.getMetadata();
      if (metadata != null) {
        String planMeta = metadata.get("teamhub_plan_key");
        if (planMeta != null) {
          try {
            return Plan.valueOf(planMeta.toUpperCase());
          } catch (IllegalArgumentException ignored) {
            // unrecognised metadata value
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "Could not extract plan from subscription {}: {}", subscription.getId(), e.getMessage());
    }
    return Plan.FREE;
  }
}
