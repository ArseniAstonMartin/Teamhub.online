package com.teamhub.billing;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed binding for all Stripe configuration values (TASK-060, PRD §17.1).
 *
 * <p>Spring fails at startup with a {@code BindException} or {@code ConstraintViolationException}
 * if any required value is blank, preventing silent misconfiguration in production.
 *
 * <p>TO-DO: Provision the following secrets per PRD §26.2 before deploying:
 *
 * <ul>
 *   <li>{@code STRIPE_SECRET_KEY} — Stripe restricted API key ({@code rk_test_...} for test, {@code
 *       rk_live_...} for production). Minimum permissions: Products read, Prices read, Checkout
 *       Sessions write, Billing Portal read/write, Webhook Events read.
 *   <li>{@code STRIPE_PRICE_FREE} — price ID from seed-products script output.
 *   <li>{@code STRIPE_PRICE_STARTER} — price ID from seed-products script output.
 *   <li>{@code STRIPE_PRICE_TEAM} — price ID from seed-products script output.
 *   <li>{@code STRIPE_PRICE_ENTERPRISE} — price ID from seed-products script output.
 *   <li>{@code STRIPE_WEBHOOK_SECRET} — signing secret from the Stripe Dashboard webhook endpoint
 *       (whsec_...).
 * </ul>
 */
@Validated
@ConfigurationProperties("teamhub.stripe")
public record StripePriceProperties(
    @NotBlank String secretKey,
    @NotBlank String priceFree,
    @NotBlank String priceStarter,
    @NotBlank String priceTeam,
    @NotBlank String priceEnterprise,
    @NotBlank String webhookSecret) {}
