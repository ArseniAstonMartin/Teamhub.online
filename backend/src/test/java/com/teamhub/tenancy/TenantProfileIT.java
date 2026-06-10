package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.dto.UpdateTenantRequest;
import com.teamhub.tenancy.dto.UpdateTenantSettingsRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for tenant profile and timezone settings (PRD §5.2, §20.5; TASK-128).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_getTenant_returnsNameAndTimezone()}): GET /api/v1/tenant.
 *   <li><b>Step 2</b> ({@link #step2_patchNameAndTimezone_getConfirmsUpdates()}): PATCH name +
 *       timezone then GET confirms.
 *   <li><b>Step 3</b> ({@link #step3_patchInvalidTimezone_returns422()}): invalid timezone → 422.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class TenantProfileIT {

  private static final String TENANT_URL = "/api/v1/tenant";

  private static final String SETTINGS_URL = "/api/v1/tenant/settings";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private String adminToken;

  private String dispatcherToken;

  private UUID adminUserId;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    String ownerEmail = "tenant-profile+" + UUID.randomUUID() + "@test.com";
    tenant =
        tx.execute(s -> tenantService.provision("Profile Co " + UUID.randomUUID(), ownerEmail));

    adminUserId =
        userRepository
            .findByEmail(ownerEmail)
            .orElseThrow(() -> new IllegalStateException("Admin user not found after provision"))
            .getId();

    adminToken = mintToken(tenant.getId(), adminUserId, UserRole.ADMIN.name());
    dispatcherToken = mintToken(tenant.getId(), UUID.randomUUID(), UserRole.DISPATCHER.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  /** TASK-128 Step 1: GET /api/v1/tenant returns name and timezone. */
  @Test
  void step1_getTenant_returnsNameAndTimezone() throws Exception {
    mockMvc
        .perform(get(TENANT_URL).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(tenant.getId().toString()))
        .andExpect(jsonPath("$.name").value(tenant.getName()))
        .andExpect(jsonPath("$.plan").value("FREE"))
        .andExpect(jsonPath("$.timezone").value(TenantTimezones.DEFAULT));
  }

  /** TASK-128 Step 2: PATCH name and timezone — GET confirms updates. */
  @Test
  void step2_patchNameAndTimezone_getConfirmsUpdates() throws Exception {
    String newName = "Renamed Team " + UUID.randomUUID();
    String newTimezone = "America/Chicago";

    mockMvc
        .perform(
            patch(TENANT_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateTenantRequest(newName))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(newName));

    mockMvc
        .perform(
            patch(SETTINGS_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateTenantSettingsRequest(null, newTimezone))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value(newTimezone));

    mockMvc
        .perform(get(TENANT_URL).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.timezone").value(newTimezone));

    mockMvc
        .perform(get(SETTINGS_URL).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value(newTimezone));

    UUID tid = tenant.getId();
    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer nameAudit =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'tenant.name.updated'",
                      Integer.class,
                      tid);
              Integer tzAudit =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'tenant.timezone.updated'",
                      Integer.class,
                      tid);
              assertThat(nameAudit).isGreaterThanOrEqualTo(1);
              assertThat(tzAudit).isGreaterThanOrEqualTo(1);
            });
  }

  /** TASK-128 Step 3: invalid timezone → 422. */
  @Test
  void step3_patchInvalidTimezone_returns422() throws Exception {
    mockMvc
        .perform(
            patch(SETTINGS_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateTenantSettingsRequest(null, "Europe/London"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value(InvalidTenantTimezoneException.CODE));
  }

  @Test
  void getTenant_asDispatcher_returns403() throws Exception {
    mockMvc
        .perform(get(TENANT_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  private String mintToken(UUID tenantId, UUID userId, String role) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", role)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
