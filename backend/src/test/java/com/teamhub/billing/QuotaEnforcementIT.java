package com.teamhub.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for plan-quota enforcement at the API boundary (PRD §17.3, TASK-064).
 *
 * <ul>
 *   <li>Step 1: Free tenant at 25-job limit — 26th job creation → 402 {@code QUOTA_EXCEEDED}.
 *   <li>Step 2: Starter tenant at 5-seat limit — 6th invite → 402 {@code SEAT_LIMIT_EXCEEDED}.
 *   <li>Step 3: Free tenant attempts schema publish → 402 {@code PLAN_FEATURE_LOCKED}.
 *   <li>Step 4: Starter tenant — all three checks pass (no 402 responses).
 * </ul>
 *
 * <p>Steps 1 and 2 use {@link SpyBean} on {@link QuotaEnforcementService} to avoid inserting 25
 * real jobs or 5 real users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class QuotaEnforcementIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private ObjectMapper objectMapper;

  @SpyBean private QuotaEnforcementService quotaEnforcementService;

  private Tenant tenant;

  private UUID adminUserId;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    Mockito.reset(quotaEnforcementService);
    tenant = tenantService.provisionWithAdminUser("Quota Corp", "quota-admin@quota.com");
    adminUserId = userRepository.findByEmail("quota-admin@quota.com").orElseThrow().getId();
    Customer customer = buildCustomer(tenant.getId());
    customerRepository.save(customer);
    customerId = customer.getId();
  }

  @AfterEach
  void resetSpy() {
    Mockito.reset(quotaEnforcementService);
  }

  // ---------------------------------------------------------------------------
  // Step 1: Free tier, monthly job limit reached → 402 QUOTA_EXCEEDED
  // ---------------------------------------------------------------------------

  /**
   * TASK-064 Step 1: Free tenant at the 25-job limit — the 26th job creation attempt must be
   * rejected with HTTP 402 and error code {@code QUOTA_EXCEEDED}.
   */
  @Test
  void step1_freeQuotaExceeded_jobCreate_returns402() throws Exception {
    tenant.setPlan(Plan.FREE);
    tenantRepository.saveAndFlush(tenant);

    doThrow(new QuotaExceededException())
        .when(quotaEnforcementService)
        .checkJobQuota(tenant.getId());

    Map<String, Object> body =
        Map.of("customerId", customerId.toString(), "scheduledStart", Instant.now().toString());

    mockMvc
        .perform(
            post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + mintToken("DISPATCHER")))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error.code").value(QuotaExceededException.CODE));
  }

  // ---------------------------------------------------------------------------
  // Step 2: Starter tier, seat limit reached → 402 SEAT_LIMIT_EXCEEDED
  // ---------------------------------------------------------------------------

  /**
   * TASK-064 Step 2: Starter tenant at the 5-seat limit — the 6th invite attempt must be rejected
   * with HTTP 402 and error code {@code SEAT_LIMIT_EXCEEDED}.
   */
  @Test
  void step2_starterSeatLimitExceeded_inviteCreate_returns402() throws Exception {
    tenant.setPlan(Plan.STARTER);
    tenantRepository.saveAndFlush(tenant);

    doThrow(new SeatLimitExceededException())
        .when(quotaEnforcementService)
        .checkSeatQuota(tenant.getId());

    Map<String, Object> body = Map.of("email", "newuser@example.com", "role", "DISPATCHER");

    mockMvc
        .perform(
            post("/api/v1/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + mintToken("ADMIN")))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error.code").value(SeatLimitExceededException.CODE));
  }

  // ---------------------------------------------------------------------------
  // Step 3: Free tier → schema editor locked → 402 PLAN_FEATURE_LOCKED
  // ---------------------------------------------------------------------------

  /**
   * TASK-064 Step 3: Free tenant attempting to publish a schema must receive HTTP 402 with error
   * code {@code PLAN_FEATURE_LOCKED}. No spy stubbing required — the check is plan-based.
   */
  @Test
  void step3_freePlan_schemaPublish_returns402() throws Exception {
    tenant.setPlan(Plan.FREE);
    tenantRepository.saveAndFlush(tenant);

    // Real checkSchemaEditorAccess reads the DB; reset to call through normally.
    doCallRealMethod().when(quotaEnforcementService).checkSchemaEditorAccess(any());

    Map<String, Object> fieldDef =
        Map.of("key", "notes", "label", "Notes", "type", "TEXT", "required", false);
    Map<String, Object> body = Map.of("name", "Test Schema", "fieldDefinitions", List.of(fieldDef));

    mockMvc
        .perform(
            post("/api/v1/schemas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + mintToken("ADMIN")))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error.code").value(PlanFeatureLockedException.CODE));
  }

  /**
   * TASK-064 Step 3 (deactivate): Free tenant attempting to deactivate a schema must also receive
   * HTTP 402 {@code PLAN_FEATURE_LOCKED}.
   */
  @Test
  void step3b_freePlan_schemaDeactivate_returns402() throws Exception {
    tenant.setPlan(Plan.FREE);
    tenantRepository.saveAndFlush(tenant);

    doCallRealMethod().when(quotaEnforcementService).checkSchemaEditorAccess(any());

    mockMvc
        .perform(
            patch("/api/v1/schemas/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + mintToken("ADMIN")))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error.code").value(PlanFeatureLockedException.CODE));
  }

  // ---------------------------------------------------------------------------
  // Step 4: Starter plan — checks pass (no 402)
  // ---------------------------------------------------------------------------

  /**
   * TASK-064 Step 4: Starter tenant with capacity remaining — job quota and seat quota checks pass
   * (no 402). Schema editor is enabled on Starter.
   */
  @Test
  void step4_starterPlan_quotaAndSchemaChecksPass() throws Exception {
    tenant.setPlan(Plan.STARTER);
    tenantRepository.saveAndFlush(tenant);

    // Let all real checks run through.
    doCallRealMethod().when(quotaEnforcementService).checkJobQuota(any());
    doCallRealMethod().when(quotaEnforcementService).checkSeatQuota(any());
    doCallRealMethod().when(quotaEnforcementService).checkSchemaEditorAccess(any());

    // Job creation should not be blocked (1 job << 200 limit).
    Map<String, Object> jobBody =
        Map.of("customerId", customerId.toString(), "scheduledStart", Instant.now().toString());
    mockMvc
        .perform(
            post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobBody))
                .header("Authorization", "Bearer " + mintToken("DISPATCHER")))
        .andExpect(
            result -> {
              int status = result.getResponse().getStatus();
              assert status != 402 : "Expected non-402 but got 402 QUOTA_EXCEEDED";
            });

    // Schema publish should not be blocked (Starter has schemaEditorEnabled=true).
    Map<String, Object> fieldDef =
        Map.of("key", "notes", "label", "Notes", "type", "TEXT", "required", false);
    Map<String, Object> schemaBody =
        Map.of("name", "Starter Schema", "fieldDefinitions", List.of(fieldDef));
    mockMvc
        .perform(
            post("/api/v1/schemas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(schemaBody))
                .header("Authorization", "Bearer " + mintToken("ADMIN")))
        .andExpect(
            result -> {
              int status = result.getResponse().getStatus();
              assert status != 402 : "Expected non-402 but got 402 PLAN_FEATURE_LOCKED";
            });
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Customer buildCustomer(UUID tenantId) {
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    Customer c = new Customer();
    c.setTenantId(tenantId);
    c.setName("Test Customer");
    c.setPhone("+15033334444");
    c.setAddressLine("100 Test Ave");
    c.setCity("Hillsboro");
    c.setState("OR");
    c.setZip("97124");
    c.setLocation(gf.createPoint(new Coordinate(-122.9898, 45.5229)));
    return c;
  }

  private String mintToken(String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(adminUserId.toString())
            .claim("tenant_id", tenant.getId().toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
