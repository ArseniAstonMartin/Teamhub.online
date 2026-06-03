package com.teamhub.billing;

import com.teamhub.billing.dto.CheckoutSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing endpoints for subscription plan management (PRD §17.2, TASK-061).
 *
 * <p>All endpoints require {@code PERM_billing.manage}, which is held by ADMIN only (V24
 * migration). The Stripe Checkout Session URL returned by POST checkout/{priceKey} is used by the
 * frontend to redirect the browser to Stripe's hosted payment page.
 */
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "billing", description = "Billing and subscription management — PRD §17.2")
public class BillingController {

  private final BillingService billingService;

  public BillingController(BillingService billingService) {
    this.billingService = billingService;
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
}
