package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.job.dto.CreateJobRequest;
import com.teamhub.job.dto.UpdateJobRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end HTTP tests for {@code /api/v1/jobs} (PRD §9, §10, §22, TASK-044).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_dispatcher_createJob_submittedEventInOutbox()}): Dispatcher
 *       {@code POST /jobs} → 201; {@code JobSubmitted} event in {@code outbox_events} in same tx.
 *   <li><b>Step 2</b> ({@link #step2_technician_listJobs_seesOnlyOwnAndSubmitted()}): Technician
 *       {@code GET /jobs} → only own + {@code SUBMITTED} (shared-pool) jobs; other-tenant jobs not
 *       returned.
 *   <li><b>Step 3</b> ({@link #step3_dispatcher_hardDelete_returns403()}): Dispatcher {@code DELETE
 *       /jobs/{id}/hard-delete} → 403 {@code FORBIDDEN}.
 *   <li><b>Step 4</b> ({@link #step4_admin_hardDelete_returns204_andRowGone()}): Admin {@code
 *       DELETE /jobs/{id}/hard-delete} → 204; row gone from DB.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JobControllerIT {

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

  private String adminToken;

  private String dispatcherToken;

  private UUID technicianUserId;

  private String technicianToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Job Ctrl Co " + UUID.randomUUID(),
                    "jobctrl+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    technicianUserId = UUID.randomUUID();

    // Insert a real user row so that jobs.assigned_technician_id FK is satisfied.
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              technicianUserId,
              tid,
              "tech-" + technicianUserId + "@test.com");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Test Customer");
              c.setPhone("+15031112222");
              c.setAddressLine("1 Test St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    adminToken = mintToken(tid, UUID.randomUUID(), UserRole.ADMIN.name());
    dispatcherToken = mintToken(tid, UUID.randomUUID(), UserRole.DISPATCHER.name());
    technicianToken = mintToken(tid, technicianUserId, UserRole.TECHNICIAN.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  // =========================================================================
  // Step 1 — Dispatcher POST /jobs → 201; JobSubmitted event in outbox in same tx
  // =========================================================================

  /**
   * TASK-044 Step 1: Dispatcher creates a job; response is 201 with job body; the {@code
   * JobSubmitted} outbox event exists in the same committed transaction.
   */
  @Test
  void step1_dispatcher_createJob_submittedEventInOutbox() throws Exception {
    CreateJobRequest req =
        new CreateJobRequest(
            customerId,
            null,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.SECONDS),
            OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .plusHours(2)
                .truncatedTo(ChronoUnit.SECONDS),
            45.5231,
            -122.6784,
            Map.of(),
            "Test job note");

    MvcResult result =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.customerId").value(customerId.toString()))
            .andReturn();

    String jobId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

    // Verify outbox event in DB
    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'JobSubmitted' AND aggregate_id = ?::uuid",
            Integer.class,
            tenant.getId(),
            jobId);
    assertThat(outboxCount).isEqualTo(1);
  }

  // =========================================================================
  // Step 2 — Technician GET /jobs returns only own + SUBMITTED; other-tenant not returned
  // =========================================================================

  /**
   * TASK-044 Step 2: Technician list returns only jobs where {@code assignedTechnicianId =
   * technicianUserId OR status = SUBMITTED}. Jobs assigned to another technician or belonging to
   * another tenant are excluded.
   */
  @Test
  void step2_technician_listJobs_seesOnlyOwnAndSubmitted() throws Exception {
    UUID tid = tenant.getId();

    // Job 1: SUBMITTED (shared pool) — technician should see it
    UUID job1Id = createJobDirect(tid, JobStatus.SUBMITTED, null);

    // Job 2: ASSIGNED to this technician — should see it
    UUID job2Id = createJobDirect(tid, JobStatus.ASSIGNED_TO_TECHNICIAN, technicianUserId);

    // Job 3: NOT assigned to this technician, NOT in shared pool — technician should NOT see it
    UUID job3Id = createJobDirect(tid, JobStatus.IN_PROGRESS, null);

    // Job 4: SUBMITTED in a DIFFERENT tenant — should NOT see it
    Tenant otherTenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Other Co " + UUID.randomUUID(), "other+" + UUID.randomUUID() + "@test.com"));
    UUID otherCustomerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(otherTenant.getId());
              c.setName("Other Customer");
              c.setPhone("+15039998888");
              c.setAddressLine("2 Other St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', '{}', 0)",
              otherTenant.getId(),
              otherCustomerId);
          return null;
        });

    MvcResult result =
        mockMvc
            .perform(get(BASE_URL).header("Authorization", "Bearer " + technicianToken))
            .andExpect(status().isOk())
            .andReturn();

    com.fasterxml.jackson.databind.JsonNode body =
        objectMapper.readTree(result.getResponse().getContentAsString());
    int total = body.get("totalElements").asInt();
    assertThat(total).isEqualTo(2);

    java.util.Set<String> returnedIds = new java.util.HashSet<>();
    body.get("content").forEach(n -> returnedIds.add(n.get("id").asText()));
    assertThat(returnedIds).contains(job1Id.toString(), job2Id.toString());
    assertThat(returnedIds).doesNotContain(job3Id.toString());

    // Clean up other tenant
    UUID otid = otherTenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", otid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", otid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", otid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", otid);
          return null;
        });
  }

  // =========================================================================
  // Step 3 — Dispatcher hard-delete → 403 FORBIDDEN
  // =========================================================================

  /**
   * TASK-044 Step 3: Dispatcher lacks {@code PERM_job.hard_delete}; hard-delete attempt must return
   * 403 with code {@code FORBIDDEN}.
   */
  @Test
  void step3_dispatcher_hardDelete_returns403() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            delete(BASE_URL + "/" + jobId + "/hard-delete")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // =========================================================================
  // Step 4 — Admin hard-delete → 204, row gone from DB
  // =========================================================================

  /**
   * TASK-044 Step 4: Admin hard-delete returns 204; the job row is physically removed from the
   * database.
   */
  @Test
  void step4_admin_hardDelete_returns204_andRowGone() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            delete(BASE_URL + "/" + jobId + "/hard-delete")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    // Verify row is gone
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM jobs WHERE id = ?::uuid", Integer.class, jobId.toString());
    assertThat(count).isEqualTo(0);
  }

  // =========================================================================
  // Additional: unauthenticated access → 401
  // =========================================================================

  @Test
  void listJobs_withoutToken_returns401() throws Exception {
    mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
  }

  // =========================================================================
  // Additional: Technician cannot create a job → 403
  // =========================================================================

  @Test
  void technician_createJob_returns403() throws Exception {
    CreateJobRequest req =
        new CreateJobRequest(
            customerId,
            null,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + technicianToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // =========================================================================
  // Additional: GET /jobs/{id} returns the job; 404 for unknown id
  // =========================================================================

  @Test
  void getById_dispatcher_returnsJob() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    mockMvc
        .perform(get(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  @Test
  void getById_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            get(BASE_URL + "/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
  }

  // =========================================================================
  // Additional: PATCH /jobs/{id} updates non-status fields
  // =========================================================================

  @Test
  void patch_dispatcher_updatesScheduledStart() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    OffsetDateTime newStart =
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).truncatedTo(ChronoUnit.SECONDS);
    UpdateJobRequest patchReq =
        new UpdateJobRequest(null, null, newStart, null, null, null, null, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + jobId)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  // =========================================================================
  // Additional: soft-archive (DELETE /jobs/{id}) by dispatcher → 204; excluded from list
  // =========================================================================

  @Test
  void dispatcher_softArchive_excludedFromList() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    mockMvc
        .perform(
            delete(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    // Job no longer appears in the default list
    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // But row still exists in DB
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM jobs WHERE id = ?::uuid AND archived_at IS NOT NULL",
            Integer.class,
            jobId.toString());
    assertThat(count).isEqualTo(1);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private UUID createJobDirect(UUID tenantId, JobStatus status, UUID assignedTechnicianId) {
    return tx.execute(
        s -> {
          UUID jobId =
              jdbc.queryForObject(
                  "INSERT INTO jobs (tenant_id, customer_id, status,"
                      + " assigned_technician_id, custom_fields, version)"
                      + " VALUES (?::uuid, ?::uuid, ?, ?::uuid, '{}', 0) RETURNING id",
                  UUID.class,
                  tenantId,
                  customerId,
                  status.name(),
                  assignedTechnicianId != null ? assignedTechnicianId.toString() : null);
          return jobId;
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
