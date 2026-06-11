package com.teamhub.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.shared.TenantContext;
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
 * End-to-end HTTP integration tests for {@code GET /api/v1/jobs/{id}/status-history} (PRD §9;
 * TASK-124).
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class JobStatusHistoryIT {

  private static final String BASE_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JobStateMachineService stateMachine;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private Tenant otherTenant;

  private UUID customerId;

  private UUID technicianId;

  private String adminToken;

  private String otherTenantToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);

    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "History Co " + UUID.randomUUID(),
                    "history-owner+" + UUID.randomUUID() + "@test.com"));

    otherTenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Other History Co " + UUID.randomUUID(),
                    "other-history+" + UUID.randomUUID() + "@test.com"));

    technicianId = UUID.randomUUID();

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tenant.getId());
              c.setName("Test Customer");
              c.setPhone("+15551234567");
              c.setAddressLine("1 History Ln");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-73.9857, 40.7484)));
              customerRepository.save(c);
              return c.getId();
            });

    adminToken = mintToken(tenant.getId(), UUID.randomUUID(), UserRole.ADMIN.name());
    otherTenantToken = mintToken(otherTenant.getId(), UUID.randomUUID(), UserRole.ADMIN.name());
  }

  @AfterEach
  void cleanUp() {
    UUID tid = tenant.getId();
    UUID otherTid = otherTenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);

          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", otherTid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", otherTid);
          return null;
        });
  }

  /**
   * TASK-124 Step 1: after SUBMITTED → ASSIGNED → IN_PROGRESS transitions, GET returns rows in
   * oldest-first order with the documented fields.
   */
  @Test
  void step1_submittedAssignedInProgress_returnsHistoryInOrder() throws Exception {
    UUID jobId = createJob(JobStatus.SUBMITTED, null);

    TenantContext.set(tenant.getId());
    try {
      stateMachine.transition(
          jobId, JobStatus.ASSIGNED_TO_TECHNICIAN, technicianId, "TECHNICIAN", "Claimed from pool");
      stateMachine.transition(
          jobId, JobStatus.IN_PROGRESS, technicianId, "TECHNICIAN", "Started on site");
    } finally {
      TenantContext.clear();
    }

    mockMvc
        .perform(
            get(BASE_URL + "/" + jobId + "/status-history")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].fromStatus").value("SUBMITTED"))
        .andExpect(jsonPath("$[0].toStatus").value("ASSIGNED_TO_TECHNICIAN"))
        .andExpect(jsonPath("$[0].actorRole").value("TECHNICIAN"))
        .andExpect(jsonPath("$[0].transitionNote").value("Claimed from pool"))
        .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$[1].fromStatus").value("ASSIGNED_TO_TECHNICIAN"))
        .andExpect(jsonPath("$[1].toStatus").value("IN_PROGRESS"))
        .andExpect(jsonPath("$[1].actorRole").value("TECHNICIAN"))
        .andExpect(jsonPath("$[1].transitionNote").value("Started on site"))
        .andExpect(jsonPath("$[1].createdAt").isNotEmpty());
  }

  /** TASK-124 Step 3: a user from another tenant receives 404 {@code JOB_NOT_FOUND}. */
  @Test
  void step3_anotherTenant_listStatusHistory_returns404() throws Exception {
    UUID jobId = createJob(JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            get(BASE_URL + "/" + jobId + "/status-history")
                .header("Authorization", "Bearer " + otherTenantToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(JobNotFoundException.CODE));
  }

  /** Additional: empty history returns an empty JSON array. */
  @Test
  void listStatusHistory_noTransitions_returnsEmptyArray() throws Exception {
    UUID jobId = createJob(JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            get(BASE_URL + "/" + jobId + "/status-history")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  private UUID createJob(JobStatus status, UUID assignedTechnicianId) {
    return tx.execute(
        s ->
            jdbc.queryForObject(
                "INSERT INTO jobs (tenant_id, customer_id, status,"
                    + " assigned_technician_id, custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, ?, ?::uuid, '{}', 0) RETURNING id",
                UUID.class,
                tenant.getId(),
                customerId,
                status.name(),
                assignedTechnicianId != null ? assignedTechnicianId.toString() : null));
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
