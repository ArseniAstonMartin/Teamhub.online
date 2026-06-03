package com.teamhub.billing;

import com.stripe.exception.SignatureVerificationException;
import com.teamhub.billing.dto.CheckoutSessionResponse;
import com.teamhub.billing.dto.PortalSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing endpoints for subscription plan management (PRD §17.2, TASK-061 / TASK-062 / TASK-063).
 *
 * <p>Authenticated endpoints ({@code checkout} and {@code portal}) require {@code
 * PERM_billing.manage} (ADMIN only, V24 migration).
 *
 * <p>The webhook endpoint ({@code POST /webhook}) is permit-all (Stripe calls it server-to-server);
 * its authenticity is verified by HMAC signature inside {@link WebhookHandlerService}.
 */
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "billing", description = "Billing and subscription management — PRD §17.2")
public class BillingController {

  private final BillingService billingService;

  private final WebhookHandlerService webhookHandlerService;

  public BillingController(
      BillingService billingService, WebhookHandlerService webhookHandlerService) {
    this.billingService = billingService;
    this.webhookHandlerService = webhookHandlerService;
  }

  /**
   * Creates a Stripe Checkout Session for the requested plan upgrade.
   *
   * @param priceKey plan identifier: "free", "starter", "team", or "enterprise"
   * @return 200 with {@code { "url": "<stripe-hosted-checkout-url>" }}
   * @throws InvalidPriceKeyException (400) if {@code priceKey} is unrecognised
   * @throws BillingServiceException (502) if the Stripe API call fails
   */
  @PostMapping("/checkout/{priceKey}")
  @PreAuthorize("hasAuthority('PERM_billing.manage')")
  @Operation(summary = "Create a Stripe Checkout Session for a subscription plan upgrade")
  public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
      @PathVariable String priceKey, Authentication authentication) {

    JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
    UUID tenantId = UUID.fromString(jwtAuth.getToken().getClaimAsString("tenant_id"));
    UUID actorUserId = UUID.fromString(jwtAuth.getToken().getSubject());

    String url = billingService.createCheckoutSession(tenantId, priceKey, actorUserId);
    return ResponseEntity.ok(new CheckoutSessionResponse(url));
  }

  /**
   * Opens a Stripe Customer Portal session so the customer can manage their plan, payment method,
   * and billing history (PRD §17.2, TASK-062).
   *
   * @return 200 with {@code { "url": "<stripe-hosted-portal-url>" }}
   * @throws BillingServiceException (502) if no Stripe customer exists or the Stripe API fails
   */
  @PostMapping("/portal")
  @PreAuthorize("hasAuthority('PERM_billing.manage')")
  @Operation(summary = "Create a Stripe Customer Portal session")
  public ResponseEntity<PortalSessionResponse> createPortalSession(Authentication authentication) {
    JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
    UUID tenantId = UUID.fromString(jwtAuth.getToken().getClaimAsString("tenant_id"));

    String url = billingService.createPortalSession(tenantId);
    return ResponseEntity.ok(new PortalSessionResponse(url));
  }

  /**
   * Stripe webhook receiver (PRD §17.3, TASK-063).
   *
   * <p>This endpoint is permit-all (no JWT required); Stripe calls it server-to-server. The {@code
   * Stripe-Signature} header is verified with HMAC-SHA256 using {@code STRIPE_WEBHOOK_SECRET}.
   * Returns 400 on invalid signature, 200 on success.
   *
   * @param payload raw JSON body exactly as received from Stripe (must not be parsed before this
   *     handler)
   * @param sigHeader value of the {@code Stripe-Signature} HTTP header
   */
  @PostMapping("/webhook")
  @Operation(summary = "Stripe webhook receiver — verified by HMAC signature")
  public ResponseEntity<Void> handleWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
    try {
      webhookHandlerService.handleEvent(payload, sigHeader);
      return ResponseEntity.ok().build();
    } catch (SignatureVerificationException e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
