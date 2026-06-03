package com.teamhub.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRepository;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates Stripe Checkout Session creation for plan upgrades (PRD §17.2, TASK-061).
 *
 * <p>Stripe Customer creation is lazy: the first checkout call creates a Stripe Customer and
 * persists its ID on the tenant. Subsequent calls reuse the stored ID.
 */
@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);

  private static final String STRIPE_API_VERSION = "2026-04-22.dahlia";

  private static final String SUCCESS_URL =
      "https://app.teamhub.online/settings/billing?checkout=success&session_id={CHECKOUT_SESSION_ID}";

  private static final String CANCEL_URL =
      "https://app.teamhub.online/settings/billing?checkout=cancelled";

  private final TenantRepository tenantRepository;

  private final UserRepository userRepository;

  private final StripePriceProperties stripeProperties;

  public BillingService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      StripePriceProperties stripeProperties) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.stripeProperties = stripeProperties;
  }

  /**
   * Creates a Stripe Checkout Session for the given tenant and price key.
   *
   * @param tenantId the tenant requesting a plan upgrade
   * @param priceKey one of: "free", "starter", "team", "enterprise"
   * @param actorUserId the authenticated user initiating the checkout (used for Stripe Customer
   *     email on first-time creation)
   * @return the Stripe-hosted Checkout Session URL to redirect the browser to
   * @throws InvalidPriceKeyException if {@code priceKey} is not a recognised plan key
   * @throws BillingServiceException if any Stripe API call fails
   */
  @Transactional
  public String createCheckoutSession(UUID tenantId, String priceKey, UUID actorUserId) {
    String priceId = resolvePriceId(priceKey);

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

    RequestOptions requestOptions =
        RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(
                RequestOptions.builder(), STRIPE_API_VERSION)
            .build();

    ensureStripeCustomer(tenant, actorUserId, requestOptions);

    return createSession(tenant.getStripeCustomerId(), priceId, requestOptions);
  }

  private void ensureStripeCustomer(Tenant tenant, UUID actorUserId, RequestOptions options) {
    if (tenant.getStripeCustomerId() != null) {
      return;
    }

    User actor =
        userRepository
            .findById(actorUserId)
            .orElseThrow(() -> new IllegalStateException("Actor user not found: " + actorUserId));

    CustomerCreateParams params =
        CustomerCreateParams.builder().setEmail(actor.getEmail()).setName(tenant.getName()).build();

    try {
      Customer stripeCustomer = Customer.create(params, options);
      tenant.setStripeCustomerId(stripeCustomer.getId());
      tenantRepository.save(tenant);
      log.info("Created Stripe Customer {} for tenant {}", stripeCustomer.getId(), tenant.getId());
    } catch (StripeException e) {
      log.error("Failed to create Stripe Customer for tenant {}", tenant.getId(), e);
      throw new BillingServiceException("Failed to create Stripe customer", e);
    }
  }

  private String createSession(String customerId, String priceId, RequestOptions options) {
    SessionCreateParams params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(
                SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
            .setSuccessUrl(SUCCESS_URL)
            .setCancelUrl(CANCEL_URL)
            .build();

    try {
      Session session = Session.create(params, options);
      return session.getUrl();
    } catch (StripeException e) {
      log.error("Failed to create Stripe Checkout Session", e);
      throw new BillingServiceException("Failed to create Stripe Checkout Session", e);
    }
  }

  private String resolvePriceId(String priceKey) {
    return switch (priceKey) {
      case "free" -> stripeProperties.priceFree();
      case "starter" -> stripeProperties.priceStarter();
      case "team" -> stripeProperties.priceTeam();
      case "enterprise" -> stripeProperties.priceEnterprise();
      default -> throw new InvalidPriceKeyException(priceKey);
    };
  }
}
