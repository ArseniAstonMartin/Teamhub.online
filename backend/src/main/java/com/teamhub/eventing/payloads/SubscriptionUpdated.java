package com.teamhub.eventing.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

// Append-only payload schema — add fields with defaults; never remove or rename existing fields
// (PRD §22.5.5).

/**
 * Payload emitted by the Stripe webhook handler on {@code customer.subscription.updated} (PRD
 * §22.5.5).
 *
 * <p>Consumer groups: {@code tenant-plan-updater}, {@code audit-logger}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionUpdated(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("stripe_subscription_id") String stripeSubscriptionId,
    @JsonProperty("old_plan") String oldPlan,
    @JsonProperty("new_plan") String newPlan,
    @JsonProperty("effective_at") Instant effectiveAt) {}
