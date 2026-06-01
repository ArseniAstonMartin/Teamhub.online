package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * End-to-end HTTP integration tests for {@code POST /api/v1/jobs/{id}/claim} (PRD §10.2, TASK-045).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_concurrentClaim_exactlyOneSucceedsOneConflicts()}): Two
 *       technicians race to claim the same SUBMITTED job concurrently — exactly one 200, one 409
 *       {@code JOB_ALREADY_CLAIMED}.
 *   <li><b>Step 2</b> ({@link #step2_claimAlreadyAssignedJob_returns409()}): Claiming an
 *       already-assigned job returns 409 {@code JOB_ALREADY_CLAIMED}.
 *   <li><b>Step 3</b> ({@link #step3_submittedJob_phoneNull_claimedJob_phonePresent()}): GET
 *       unclaimed job → {@code customerPhone} absent; GET same job after claim → {@code
 *       customerPhone} present.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JobClaimIT {

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

  private UUID technicianUserId1;

  private UUID technicianUserId2;

  private String technicianToken1;

  private String technicianToken2;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Claim Co " + UUID.randomUUID(), "claim+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    technicianUserId1 = UUID.randomUUID();
    technicianUserId2 = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              technicianUserId1,
              tid,
              "tech1-" + technicianUserId1 + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              technicianUserId2,
              tid,
              "tech2-" + technicianUserId2 + "@test.com");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Claim Customer");
              c.setPhone("+15031234567");
              c.setAddressLine("1 Claim St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    technicianToken1 = mintToken(tid, technicianUserId1, UserRole.TECHNICIAN.name());
    technicianToken2 = mintToken(tid, technicianUserId2, UserRole.TECHNICIAN.name());
    dispatcherToken = mintToken(tid, UUID.randomUUID(), UserRole.DISPATCHER.name());
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
  // Step 1 — Concurrent claim race: exactly one 200, one 409
  // =========================================================================

  /**
   * TASK-045 Step 1: Two technicians fire {@code POST /jobs/{id}/claim} concurrently. The
   * optimistic-lock native UPDATE guarantees exactly one succeeds (200) and one receives 409 {@code
   * JOB_ALREADY_CLAIMED}.
   */
  @Test
  void step1_concurrentClaim_exactlyOneSucceedsOneConflicts() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch goLatch = new CountDownLatch(1);

    Callable<MvcResult> claimWith1 =
        () -> {
          readyLatch.countDown();
          goLatch.await(5, TimeUnit.SECONDS);
          return mockMvc
              .perform(
                  post(BASE_URL + "/" + jobId + "/claim")
                      .header("Authorization", "Bearer " + technicianToken1))
              .andReturn();
        };

    Callable<MvcResult> claimWith2 =
        () -> {
          readyLatch.countDown();
          goLatch.await(5, TimeUnit.SECONDS);
          return mockMvc
              .perform(
                  post(BASE_URL + "/" + jobId + "/claim")
                      .header("Authorization", "Bearer " + technicianToken2))
              .andReturn();
        };

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<MvcResult> future1 = pool.submit(claimWith1);
      Future<MvcResult> future2 = pool.submit(claimWith2);

      readyLatch.await(5, TimeUnit.SECONDS);
      goLatch.countDown();

      MvcResult r1 = future1.get(10, TimeUnit.SECONDS);
      MvcResult r2 = future2.get(10, TimeUnit.SECONDS);

      int status1 = r1.getResponse().getStatus();
      int status2 = r2.getResponse().getStatus();

      List<Integer> statuses = new ArrayList<>(List.of(status1, status2));
      statuses.sort(Integer::compareTo);
      assertThat(statuses).containsExactly(200, 409);

      // Confirm the 409 response carries the correct machine-readable code
      MvcResult conflictResult = status1 == 409 ? r1 : r2;
      JsonNode body = objectMapper.readTree(conflictResult.getResponse().getContentAsString());
      assertThat(body.path("error").path("code").asText())
          .isEqualTo(JobAlreadyClaimedException.CODE);

      // Confirm exactly one JobClaimed event in outbox
      Integer outboxCount =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM outbox_events"
                  + " WHERE tenant_id = ? AND event_type = 'JobClaimed' AND aggregate_id = ?::uuid",
              Integer.class,
              tenant.getId(),
              jobId.toString());
      assertThat(outboxCount).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  // =========================================================================
  // Step 2 — Claiming an already-ASSIGNED job returns 409
  // =========================================================================

  /**
   * TASK-045 Step 2: Attempting to claim a job that is already {@code ASSIGNED_TO_TECHNICIAN}
   * returns 409 with code {@code JOB_ALREADY_CLAIMED}.
   */
  @Test
  void step2_claimAlreadyAssignedJob_returns409() throws Exception {
    UUID jobId =
        createJobDirect(tenant.getId(), JobStatus.ASSIGNED_TO_TECHNICIAN, technicianUserId1);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/claim")
                .header("Authorization", "Bearer " + technicianToken2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value(JobAlreadyClaimedException.CODE));
  }

  // =========================================================================
  // Step 3 — Phone masking: SUBMITTED → null, ASSIGNED → present
  // =========================================================================

  /**
   * TASK-045 Step 3: {@code GET /jobs/{id}} on an unclaimed (SUBMITTED) job returns {@code
   * customerPhone = null}. After a successful claim the same GET returns the phone.
   */
  @Test
  void step3_submittedJob_phoneNull_claimedJob_phonePresent() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);

    // GET unclaimed job — customerPhone must be absent/null (PRD §10.1)
    mockMvc
        .perform(get(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.customerPhone").doesNotExist());

    // Technician claims the job
    MvcResult claimResult =
        mockMvc
            .perform(
                post(BASE_URL + "/" + jobId + "/claim")
                    .header("Authorization", "Bearer " + technicianToken1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED_TO_TECHNICIAN"))
            .andReturn();

    // Claim response itself must expose the phone
    JsonNode claimBody = objectMapper.readTree(claimResult.getResponse().getContentAsString());
    assertThat(claimBody.path("customerPhone").asText()).isEqualTo("+15031234567");

    // GET same job after claim — phone now visible
    mockMvc
        .perform(get(BASE_URL + "/" + jobId).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED_TO_TECHNICIAN"))
        .andExpect(jsonPath("$.customerPhone").value("+15031234567"));
  }

  // =========================================================================
  // Additional: unauthenticated claim attempt returns 401
  // =========================================================================

  @Test
  void claimWithoutToken_returns401() throws Exception {
    UUID jobId = createJobDirect(tenant.getId(), JobStatus.SUBMITTED, null);
    mockMvc.perform(post(BASE_URL + "/" + jobId + "/claim")).andExpect(status().isUnauthorized());
  }

  // =========================================================================
  // Additional: claiming unknown job returns 409 JOB_ALREADY_CLAIMED
  // =========================================================================

  @Test
  void claimUnknownJob_returns409() throws Exception {
    mockMvc
        .perform(
            post(BASE_URL + "/" + UUID.randomUUID() + "/claim")
                .header("Authorization", "Bearer " + technicianToken1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value(JobAlreadyClaimedException.CODE));
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
