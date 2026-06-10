package com.teamhub.alert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
 * Integration tests for {@code /api/v1/alerts} (PRD §28, TASK-118).
 *
 * <ol>
 *   <li>{@link #getPending_returnsUndismissedAlertsForUser()} — user A sees only their own alert
 *   <li>{@link #dismiss_marksAlertDismissed()} — after dismiss the GET returns empty list
 *   <li>{@link #dismiss_forbiddenForWrongUser()} — user B cannot dismiss user A's alert (403)
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class InAppAlertIT {

  private static final String BASE_URL = "/api/v1/alerts";

  @Autowired private MockMvc mockMvc;

  @Autowired private InAppAlertRepository alertRepository;

  @Autowired private InAppAlertService alertService;

  @Autowired private InternalTenantService tenantService;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID tenantId;

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  private UUID userAId;

  private UUID userBId;

  /** A real job row so that in_app_alerts.job_id FK is satisfied. */
  private UUID jobId;

  private String tokenA;

  private String tokenB;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tenantService.provision(
            "Alert Co " + UUID.randomUUID(), "alert+" + UUID.randomUUID() + "@test.com");
    tenantId = tenant.getId();

    userAId = UUID.randomUUID();
    userBId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'ADMIN')",
              userAId,
              tenantId,
              "admin-a-" + userAId + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'ADMIN')",
              userBId,
              tenantId,
              "admin-b-" + userBId + "@test.com");

          // Insert a minimal customer and job so that the job_id FK is satisfied.
          UUID customerId = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO customers (id, tenant_id, name, phone, address_line, city, state, zip, location)"
                  + " VALUES (?::uuid, ?::uuid, 'Test', '+15030000000', '1 St', 'Portland', 'OR', '97201',"
                  + " ST_SetSRID(ST_MakePoint(-122.67, 45.52), 4326))",
              customerId,
              tenantId);

          jobId =
              jdbc.queryForObject(
                  "INSERT INTO jobs (tenant_id, customer_id, status, custom_fields, version)"
                      + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', '{}', 0) RETURNING id",
                  UUID.class,
                  tenantId,
                  customerId);
          return null;
        });

    tokenA = mintToken(tenantId, userAId, UserRole.ADMIN.name());
    tokenB = mintToken(tenantId, userBId, UserRole.ADMIN.name());
  }

  @AfterEach
  void tearDown() {
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM in_app_alerts WHERE tenant_id = ?", tenantId);
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tenantId);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tenantId);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tenantId);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
          return null;
        });
  }

  /**
   * User A has one undismissed alert; user B has one undismissed alert. GET /api/v1/alerts as user
   * A must return exactly 1 alert (user A's own).
   */
  @Test
  void getPending_returnsUndismissedAlertsForUser() throws Exception {
    alertService.createJobNotHandledAlert(tenantId, userAId, jobId);
    alertService.createJobNotHandledAlert(tenantId, userBId, jobId);

    mockMvc
        .perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].kind").value("JOB_NOT_HANDLED"))
        .andExpect(jsonPath("$[0].jobId").value(jobId.toString()));
  }

  /** After user A dismisses their alert, GET returns an empty list. */
  @Test
  void dismiss_marksAlertDismissed() throws Exception {
    InAppAlert alert = alertService.createJobNotHandledAlert(tenantId, userAId, jobId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + alert.getId() + "/dismiss")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  /** User B must receive 403 when trying to dismiss user A's alert. */
  @Test
  void dismiss_forbiddenForWrongUser() throws Exception {
    InAppAlert alert = alertService.createJobNotHandledAlert(tenantId, userAId, jobId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + alert.getId() + "/dismiss")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
        .andExpect(status().isForbidden());
  }

  // -------------------------------------------------------------------------

  private String mintToken(UUID tenantId, UUID userId, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
