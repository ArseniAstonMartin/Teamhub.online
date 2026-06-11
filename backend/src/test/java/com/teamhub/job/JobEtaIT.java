package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
 * End-to-end HTTP integration tests for {@code POST /api/v1/jobs/{id}/eta} (PRD §13.2; TASK-135).
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class JobEtaIT {

  private static final String BASE_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private Tenant otherTenant;

  private UUID customerId;

  private UUID assignedTechnicianId;

  private UUID otherTechnicianId;

  private String assignedTechToken;

  private String otherTechToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    String ownerEmail = "job-eta+" + UUID.randomUUID() + "@test.com";
    tenant =
        tx.execute(
            s ->
                tenantService.provisionWithAdminUser("JobEta Co " + UUID.randomUUID(), ownerEmail));

    otherTenant =
        tx.execute(
            s ->
                tenantService.provisionWithAdminUser(
                    "JobEta Other " + UUID.randomUUID(),
                    "other-" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    assignedTechnicianId = UUID.randomUUID();
    otherTechnicianId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              assignedTechnicianId,
              tid,
              "tech-assigned-" + assignedTechnicianId + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              otherTechnicianId,
              tid,
              "tech-other-" + otherTechnicianId + "@test.com");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("ETA Customer");
              c.setPhone("+15030003333");
              c.setAddressLine("99 ETA Ave");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    assignedTechToken = mintToken(tid, assignedTechnicianId, UserRole.TECHNICIAN.name());
    otherTechToken = mintToken(tid, otherTechnicianId, UserRole.TECHNICIAN.name());
  }

  @AfterEach
  void tearDown() {
    for (Tenant t : new Tenant[] {tenant, otherTenant}) {
      if (t == null) {
        continue;
      }
      UUID tid = t.getId();
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
            return null;
          });
    }
  }

  /**
   * TASK-135 Step 1: Assigned technician on ASSIGNED job — POST {@code { etaMinutes: 45 }} → 200,
   * status IN_PROGRESS, substatus ON_THE_WAY, scheduledEnd ≈ now+45m.
   */
  @Test
  void step1_assignedJob_defineEta_returns200_inProgressOnTheWay() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);
    Instant before = Instant.now();

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 45))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.inProgressSubstatus").value("ON_THE_WAY"))
        .andExpect(jsonPath("$.scheduledEnd").isNotEmpty());

    Instant scheduledEnd =
        jdbc.queryForObject("SELECT scheduled_end FROM jobs WHERE id = ?", Instant.class, jobId);
    assertThat(scheduledEnd)
        .isCloseTo(before.plus(45, ChronoUnit.MINUTES), within(2, ChronoUnit.SECONDS));

    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'JobStatusChanged' AND aggregate_id = ?",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(outboxCount).isEqualTo(1);
  }

  /**
   * TASK-135 Step 2: Same technician on IN_PROGRESS/WORKING job — POST {@code { etaMinutes: 30 }} →
   * 200, substatus ON_THE_WAY, scheduledEnd updated.
   */
  @Test
  void step2_inProgressJob_defineEta_updatesSubstatusAndScheduledEnd() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);
    tx.execute(
        s -> {
          jdbc.update("UPDATE jobs SET in_progress_substatus = 'WORKING' WHERE id = ?", jobId);
          return null;
        });

    Instant before = Instant.now();

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 30))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.inProgressSubstatus").value("ON_THE_WAY"));

    Instant scheduledEnd =
        jdbc.queryForObject("SELECT scheduled_end FROM jobs WHERE id = ?", Instant.class, jobId);
    assertThat(scheduledEnd)
        .isCloseTo(before.plus(30, ChronoUnit.MINUTES), within(2, ChronoUnit.SECONDS));

    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'JobStatusChanged' AND aggregate_id = ?",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(outboxCount).isZero();
  }

  /** TASK-135 Step 3: Another technician on same job — 403 {@code JOB_NOT_ASSIGNED_TO_CALLER}. */
  @Test
  void step3_unassignedTechnician_defineEta_returns403() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + otherTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 45))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value(JobNotAssignedToCallerException.CODE));
  }

  /** TASK-135 Step 4: POST {@code { etaMinutes: 4 }} — 422 validation error. */
  @Test
  void step4_etaMinutesBelowMin_returns422() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 4))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  /** Terminal status (COMPLETED) → 409 INVALID_TRANSITION. */
  @Test
  void defineEta_onCompletedJob_returns409() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.COMPLETED, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 45))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value(IllegalStateTransitionException.CODE));
  }

  /** Missing job → 404. */
  @Test
  void defineEta_missingJob_returns404() throws Exception {
    UUID missingId = UUID.randomUUID();

    mockMvc
        .perform(
            post(BASE_URL + "/" + missingId + "/eta")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 45))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(JobNotFoundException.CODE));
  }

  /** Another tenant's job → 404 (tenant isolation). */
  @Test
  void defineEta_anotherTenantJob_returns404() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);
    String otherTenantToken =
        mintToken(otherTenant.getId(), assignedTechnicianId, UserRole.TECHNICIAN.name());

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/eta")
                .header("Authorization", "Bearer " + otherTenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("etaMinutes", 45))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(JobNotFoundException.CODE));
  }

  private UUID createJobWithStatus(JobStatus status, UUID assigneeId) {
    return tx.execute(
        s ->
            jdbc.queryForObject(
                "INSERT INTO jobs (tenant_id, customer_id, status, assigned_technician_id,"
                    + " custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, ?, ?::uuid, '{}', 0) RETURNING id",
                UUID.class,
                tenant.getId(),
                customerId,
                status.name(),
                assigneeId != null ? assigneeId.toString() : null));
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
