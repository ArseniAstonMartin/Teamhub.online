package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
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
 * End-to-end HTTP integration tests for cancel and reopen endpoints (PRD §9.2, §9.4; TASK-127):
 *
 * <ul>
 *   <li>{@code POST /api/v1/jobs/{id}/cancel}
 *   <li>{@code POST /api/v1/jobs/{id}/reopen}
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class JobCancelReopenIT {

  private static final String BASE_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JobRepository jobRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  private UUID dispatcherId;

  private UUID adminId;

  private String dispatcherToken;

  private String adminToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    String ownerEmail = "cancel-reopen+" + UUID.randomUUID() + "@test.com";
    tenant =
        tx.execute(
            s -> tenantService.provision("CancelReopen Co " + UUID.randomUUID(), ownerEmail));

    UUID tid = tenant.getId();
    dispatcherId = UUID.randomUUID();
    adminId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'DISPATCHER')",
              dispatcherId,
              tid,
              "dispatcher-" + dispatcherId + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'ADMIN')",
              adminId,
              tid,
              "admin-" + adminId + "@test.com");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Cancel Customer");
              c.setPhone("+15030003333");
              c.setAddressLine("99 Cancel St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    dispatcherToken = mintToken(tid, dispatcherId, UserRole.DISPATCHER.name());
    adminToken = mintToken(tid, adminId, UserRole.ADMIN.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
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

  /** TASK-127 Step 1: Cancel IN_PROGRESS job — status CANCELED, history row written. */
  @Test
  void step1_cancelInProgressJob_statusCanceled_historyRowWritten() throws Exception {
    UUID techId = UUID.randomUUID();
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              techId,
              tenant.getId(),
              "tech-" + techId + "@test.com");
          return null;
        });

    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, techId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/cancel")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "OTHER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.cancellationReason").value("OTHER"));

    Job job = jobRepository.findById(jobId).orElseThrow();
    assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELED);
    assertThat(job.getCancellationReason()).isEqualTo(CancellationReason.OTHER);

    Integer historyCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM job_status_history"
                + " WHERE tenant_id = ? AND job_id = ? AND to_status = 'CANCELED'",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(historyCount).isEqualTo(1);

    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'JobStatusChanged' AND aggregate_id = ?",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(outboxCount).isEqualTo(1);
  }

  /** TASK-127 Step 2: Reopen — status SUBMITTED. */
  @Test
  void step2_reopenCanceledJob_statusSubmitted() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.CANCELED, null);
    tx.execute(
        s -> {
          jdbc.update("UPDATE jobs SET cancellation_reason = 'OTHER' WHERE id = ?", jobId);
          return null;
        });

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/reopen")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.cancellationReason").doesNotExist());

    Job job = jobRepository.findById(jobId).orElseThrow();
    assertThat(job.getStatus()).isEqualTo(JobStatus.SUBMITTED);
    assertThat(job.getCancellationReason()).isNull();
  }

  /** TASK-127 Step 3: DISPATCHER calls reopen — 403. */
  @Test
  void step3_dispatcherReopen_returns403() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.CANCELED, null);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/reopen")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  /** Cancel without reason body — 422. */
  @Test
  void cancelWithoutReason_returns422() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/cancel")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  private UUID createJobWithStatus(JobStatus status, UUID assignedTechnicianId) {
    return tx.execute(
        s -> {
          UUID jobId =
              jdbc.queryForObject(
                  "INSERT INTO jobs (tenant_id, customer_id, status,"
                      + " assigned_technician_id, custom_fields, version)"
                      + " VALUES (?::uuid, ?::uuid, ?, ?::uuid, '{}', 0) RETURNING id",
                  UUID.class,
                  tenant.getId(),
                  customerId,
                  status.name(),
                  assignedTechnicianId != null ? assignedTechnicianId.toString() : null);
          return jobId;
        });
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
