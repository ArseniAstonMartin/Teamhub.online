package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.teamhub.auth.UserRepository;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for POST /api/v1/billing/checkout/{priceKey} (TASK-061, PRD §17.2).
 *
 * <p>Stripe SDK calls are intercepted with {@link MockedStatic} so the tests run without a live
 * Stripe account. MockMvc is synchronous — the static mocks are active on the same thread as the
 * request handler, so interception works correctly.
 *
 * <ul>
 *   <li>Step 1: Admin POST /billing/checkout/team — returns 200 with Stripe URL; Stripe Customer is
 *       created lazily and the ID is persisted on the tenant.
 *   <li>Step 2: Second checkout reuses the existing Stripe Customer ID (no second Customer.create
 *       call).
 *   <li>Step 3: Non-Admin (DISPATCHER) → 403.
 *   <li>Step 4: Unknown price key → 400 INVALID_PRICE_KEY.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BillingCheckoutIT {

  private static final String FAKE_CUSTOMER_ID = "cus_test_acme";

  private static final String FAKE_SESSION_URL = "https://checkout.stripe.com/pay/cs_test_abc123";

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private Tenant tenant;

  private UUID adminUserId;

  @BeforeEach
  void setUp() {
    tenant = tenantService.provision("Acme Corp", "billing-admin@acme.com");
    adminUserId = userRepository.findByEmail("billing-admin@acme.com").orElseThrow().getId();
  }

  /**
   * Step 1: Admin POST /billing/checkout/team — Stripe Customer is created lazily (first call),
   * stored on the tenant, and the Checkout Session URL is returned.
   *
   * <p>TO-DO: test with Stripe test clock when webhook handler is implemented (TASK-062).
   */
  @Test
  void step1_adminCheckout_lazyCustomerCreated_returnsUrl() throws Exception {
    Customer fakeCustomer = mock(Customer.class);
    when(fakeCustomer.getId()).thenReturn(FAKE_CUSTOMER_ID);

    Session fakeSession = mock(Session.class);
    when(fakeSession.getUrl()).thenReturn(FAKE_SESSION_URL);

    try (MockedStatic<Customer> mockedCustomer = mockStatic(Customer.class);
        MockedStatic<Session> mockedSession = mockStatic(Session.class)) {

      mockedCustomer
          .when(() -> Customer.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeCustomer);
      mockedSession
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeSession);

      mockMvc
          .perform(
              post("/api/v1/billing/checkout/team")
                  .header("Authorization", "Bearer " + mintAdminToken()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.url").value(FAKE_SESSION_URL));

      Tenant updated = tenantRepository.findById(tenant.getId()).orElseThrow();
      assertThat(updated.getStripeCustomerId()).isEqualTo(FAKE_CUSTOMER_ID);
    }
  }

  /**
   * Step 2 (partial): Second checkout reuses the existing Stripe Customer ID — Customer.create is
   * never called a second time.
   *
   * <p>TO-DO: test with Stripe test clock when webhook handler is implemented (TASK-062).
   */
  @Test
  void step2_secondCheckout_reusesExistingCustomerId() throws Exception {
    tenant.setStripeCustomerId("cus_existing_123");
    tenantRepository.saveAndFlush(tenant);

    Session fakeSession = mock(Session.class);
    when(fakeSession.getUrl()).thenReturn(FAKE_SESSION_URL);

    try (MockedStatic<Customer> mockedCustomer = mockStatic(Customer.class);
        MockedStatic<Session> mockedSession = mockStatic(Session.class)) {

      mockedSession
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeSession);

      mockMvc
          .perform(
              post("/api/v1/billing/checkout/team")
                  .header("Authorization", "Bearer " + mintAdminToken()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.url").value(FAKE_SESSION_URL));

      mockMvc
          .perform(
              post("/api/v1/billing/checkout/team")
                  .header("Authorization", "Bearer " + mintAdminToken()))
          .andExpect(status().isOk());

      // Customer.create must never have been invoked — existing ID was reused both times.
      mockedCustomer.verify(
          () -> Customer.create(any(CustomerCreateParams.class), any(RequestOptions.class)),
          org.mockito.Mockito.never());
    }
  }

  /** Step 3: DISPATCHER lacks billing.manage permission → 403. */
  @Test
  void step3_dispatcher_isForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/billing/checkout/team")
                .header("Authorization", "Bearer " + mintToken("DISPATCHER", adminUserId)))
        .andExpect(status().isForbidden());
  }

  /** Step 4: Unknown price key → 400 with INVALID_PRICE_KEY code. */
  @Test
  void step4_invalidPriceKey_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/billing/checkout/invalid_key")
                .header("Authorization", "Bearer " + mintAdminToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(InvalidPriceKeyException.CODE));
  }

  private String mintAdminToken() {
    return mintToken("ADMIN", adminUserId);
  }

  private String mintToken(String roleCode, UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", tenant.getId().toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
