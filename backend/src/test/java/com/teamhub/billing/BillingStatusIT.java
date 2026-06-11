package com.teamhub.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GET /api/v1/billing/status (TASK-125, PRD §17).
 *
 * <ul>
 *   <li>Step 1: Admin GET /billing/status — all fields present.
 *   <li>Step 3: Tenant with {@code payment_failed_at} — {@code paymentFailed} is true.
 *   <li>Dispatcher lacks {@code PERM_billing.manage} → 403.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class BillingStatusIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private TenantQuotaStateRepository quotaStateRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private Tenant tenant;

  private UUID adminUserId;

  @BeforeEach
  void setUp() {
    tenant = tenantService.provision("Status Corp", "billing-status@status.com");
    adminUserId = userRepository.findByEmail("billing-status@status.com").orElseThrow().getId();
  }

  /** Step 1: Admin GET /billing/status — all response fields present. */
  @Test
  void step1_adminGetStatus_allFieldsPresent() throws Exception {
    Instant periodEnd = Instant.now().plus(20, ChronoUnit.DAYS);
    tenant.setPlan(Plan.TEAM);
    tenant.setCurrentPeriodEnd(periodEnd);
    tenantRepository.saveAndFlush(tenant);

    TenantQuotaState quotaState = new TenantQuotaState(tenant.getId(), Instant.now());
    quotaState.setJobCount(42);
    quotaStateRepository.saveAndFlush(quotaState);

    mockMvc
        .perform(
            get("/api/v1/billing/status").header("Authorization", "Bearer " + mintAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan").value("TEAM"))
        .andExpect(jsonPath("$.periodEnd").exists())
        .andExpect(jsonPath("$.paymentFailed").value(false))
        .andExpect(jsonPath("$.paymentFailedAt").isEmpty())
        .andExpect(jsonPath("$.jobsThisPeriod").value(42))
        .andExpect(jsonPath("$.seats").value(1));
  }

  /** Step 3: Tenant with payment_failed_at set — paymentFailed is true. */
  @Test
  void step3_paymentFailedAt_paymentFailedTrue() throws Exception {
    Instant failedAt = Instant.now().minus(1, ChronoUnit.DAYS);
    tenant.setPaymentFailedAt(failedAt);
    tenantRepository.saveAndFlush(tenant);

    mockMvc
        .perform(
            get("/api/v1/billing/status").header("Authorization", "Bearer " + mintAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentFailed").value(true))
        .andExpect(jsonPath("$.paymentFailedAt").exists());
  }

  /** DISPATCHER lacks billing.manage permission → 403. */
  @Test
  void dispatcher_isForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/billing/status")
                .header("Authorization", "Bearer " + mintToken("DISPATCHER", adminUserId)))
        .andExpect(status().isForbidden());
  }

  /** No quota state row — jobsThisPeriod defaults to 0. */
  @Test
  void noQuotaState_jobsThisPeriodZero() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/billing/status").header("Authorization", "Bearer " + mintAdminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobsThisPeriod").value(0))
        .andExpect(jsonPath("$.plan").value("FREE"));
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
