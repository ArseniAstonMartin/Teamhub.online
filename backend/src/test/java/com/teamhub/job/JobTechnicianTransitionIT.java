package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
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
import org.springframework.context.annotation.Import;
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
 * End-to-end HTTP integration tests for the technician-facing transition endpoints (PRD §9.2, §13,
 * TASK-047):
 *
 * <ul>
 *   <li>{@code POST /api/v1/jobs/{id}/start}
 *   <li>{@code POST /api/v1/jobs/{id}/pending}
 *   <li>{@code POST /api/v1/jobs/{id}/done-pending-approval}
 *   <li>{@code POST /api/v1/jobs/{id}/complete}
 * </ul>
 *
 * <p>Test steps (verbatim from tasks.json):
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_assignedTechnician_startJob_returns200_inProgressWorking()}):
 *       As assigned Technician, start a job → {@code IN_PROGRESS} + {@code WORKING} sub-status.
 *   <li><b>Step 2</b> ({@link #step2_pendingWithoutReason_returns422()}): {@code /pending} without
 *       {@code pendingReason} → 422.
 *   <li><b>Step 3</b> ({@link #step3_unassignedTechnician_startJob_returns403()}): Another
 *       technician (not assigned) hits {@code /start} → 403 {@code JOB_NOT_ASSIGNED_TO_CALLER}.
 * </ul>
 *
 * <p>Additional test: {@link #completeJob_whenCloseJobsDisabled_returns403()} — {@code /complete}
 * when {@code close_jobs_enabled = false} → 403 {@code CLOSE_JOBS_DISABLED}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JobTechnicianTransitionIT {

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

  private UUID assignedTechnicianId;

  private UUID otherTechnicianId;

  private String assignedTechToken;

  private String otherTechToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    String ownerEmail = "tech-transition+" + UUID.randomUUID() + "@test.com";
    tenant =
        tx.execute(
            s -> tenantService.provision("TechTransition Co " + UUID.randomUUID(), ownerEmail));

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
              c.setName("Transition Customer");
              c.setPhone("+15030002222");
              c.setAddressLine("42 Start St");
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

  // =========================================================================
  // Step 1 — Assigned technician starts job → IN_PROGRESS + WORKING
  // =========================================================================

  /**
   * TASK-047 Step 1: As the assigned Technician, {@code POST /start} on an {@code
   * ASSIGNED_TO_TECHNICIAN} job must return 200 with status {@code IN_PROGRESS} and sub-status
   * {@code WORKING}.
   */
  @Test
  void step1_assignedTechnician_startJob_returns200_inProgressWorking() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/start")
                .header("Authorization", "Bearer " + assignedTechToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.inProgressSubstatus").value("WORKING"));

    // Verify outbox event was published
    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'JobStatusChanged' AND aggregate_id = ?",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(outboxCount).isEqualTo(1);
  }

  // =========================================================================
  // Step 2 — /pending without pendingReason → 422
  // =========================================================================

  /**
   * TASK-047 Step 2: {@code POST /pending} without a {@code pendingReason} must return 422 {@code
   * VALIDATION_FAILED}.
   */
  @Test
  void step2_pendingWithoutReason_returns422() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/pending")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  // =========================================================================
  // Step 3 — Unassigned technician → 403
  // =========================================================================

  /**
   * TASK-047 Step 3: A technician not assigned to the job must receive 403 {@code
   * JOB_NOT_ASSIGNED_TO_CALLER} when calling {@code /start}.
   */
  @Test
  void step3_unassignedTechnician_startJob_returns403() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/start")
                .header("Authorization", "Bearer " + otherTechToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value(JobNotAssignedToCallerException.CODE));
  }

  // =========================================================================
  // Additional — /complete when close_jobs_enabled=false → 403 CLOSE_JOBS_DISABLED
  // =========================================================================

  /**
   * Additional: when {@code close_jobs_enabled = false} (default), {@code POST /complete} by the
   * assigned Technician must return 403 {@code CLOSE_JOBS_DISABLED}.
   */
  @Test
  void completeJob_whenCloseJobsDisabled_returns403() throws Exception {
    tx.execute(
        s -> {
          jdbc.update("UPDATE tenants SET close_jobs_enabled = false WHERE id = ?", tenant.getId());
          return null;
        });

    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/complete")
                .header("Authorization", "Bearer " + assignedTechToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value(CloseJobsDisabledException.CODE));
  }

  // =========================================================================
  // Additional — full flow: start → pending with reason → succeeds
  // =========================================================================

  /**
   * Happy-path pending: {@code IN_PROGRESS → PENDING} with a valid {@code pendingReason} returns
   * 200 with the correct status and reason.
   */
  @Test
  void pendingJob_withValidReason_returns200() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/pending")
                .header("Authorization", "Bearer " + assignedTechToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("pendingReason", "WAITING_FOR_PARTS"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.pendingReason").value("WAITING_FOR_PARTS"));
  }

  // =========================================================================
  // Additional — done-pending-approval by assigned technician
  // =========================================================================

  /** Happy-path done-pending-approval: {@code IN_PROGRESS → DONE_PENDING_APPROVAL} returns 200. */
  @Test
  void donePendingApproval_returns200() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/done-pending-approval")
                .header("Authorization", "Bearer " + assignedTechToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE_PENDING_APPROVAL"));
  }

  // =========================================================================
  // Additional — /complete when close_jobs_enabled=true succeeds
  // =========================================================================

  /**
   * When {@code close_jobs_enabled = true}, {@code POST /complete} by the assigned Technician must
   * return 200.
   */
  @Test
  void completeJob_whenCloseJobsEnabled_returns200() throws Exception {
    tx.execute(
        s -> {
          jdbc.update("UPDATE tenants SET close_jobs_enabled = true WHERE id = ?", tenant.getId());
          return null;
        });

    UUID jobId = createJobWithStatus(JobStatus.IN_PROGRESS, assignedTechnicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/complete")
                .header("Authorization", "Bearer " + assignedTechToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // =========================================================================
  // Helpers
  // =========================================================================

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
