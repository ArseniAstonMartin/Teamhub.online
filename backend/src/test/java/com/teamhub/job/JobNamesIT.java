package com.teamhub.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Integration tests for {@code customerName} / {@code assigneeName} enrichment on job endpoints
 * (PRD §11, TASK-130).
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class JobNamesIT {

  private static final String BASE_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  private UUID technicianUserId;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tx.execute(
            s ->
                tenantService.provisionWithAdminUser(
                    "Job Names Co " + UUID.randomUUID(),
                    "jobnames+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    technicianUserId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, name, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, ?, 'TECHNICIAN')",
              technicianUserId,
              tid,
              "tech-" + technicianUserId + "@test.com",
              "Jane Smith");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Acme Plumbing");
              c.setPhone("+15031112222");
              c.setAddressLine("1 Test St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    dispatcherToken = mintToken(tid, UUID.randomUUID(), UserRole.DISPATCHER.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  /** TASK-130 Step 1: GET /jobs/{id} returns customerName and assigneeName when both are set. */
  @Test
  void step1_getById_withCustomerAndAssignee_returnsBothNames() throws Exception {
    UUID jobId =
        createJobDirect(tenant.getId(), JobStatus.ASSIGNED_TO_TECHNICIAN, technicianUserId);

    mockMvc
        .perform(get(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerName").value("Acme Plumbing"))
        .andExpect(jsonPath("$.assigneeName").value("Jane Smith"));
  }

  /** TASK-130 Step 2: GET /jobs list — each row includes customerName. */
  @Test
  void step2_listJobs_eachRowHasCustomerName() throws Exception {
    createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);
    createJobDirect(tenant.getId(), JobStatus.ASSIGNED_TO_TECHNICIAN, technicianUserId);

    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].customerName").value("Acme Plumbing"))
        .andExpect(jsonPath("$.content[1].customerName").value("Acme Plumbing"));
  }

  /** TASK-130 Step 3: Unassigned job omits assigneeName from the JSON payload. */
  @Test
  void step3_unassignedJob_omitsAssigneeName() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    mockMvc
        .perform(get(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerName").value("Acme Plumbing"))
        .andExpect(jsonPath("$.assigneeName").doesNotExist());
  }

  private UUID createJobDirect(UUID tenantId, JobStatus status, UUID assignedTechnicianId) {
    return tx.execute(
        s -> {
          return jdbc.queryForObject(
              "INSERT INTO jobs (tenant_id, customer_id, status,"
                  + " assigned_technician_id, custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, ?, ?::uuid, '{}', 0) RETURNING id",
              UUID.class,
              tenantId,
              customerId,
              status.name(),
              assignedTechnicianId != null ? assignedTechnicianId.toString() : null);
        });
  }

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
