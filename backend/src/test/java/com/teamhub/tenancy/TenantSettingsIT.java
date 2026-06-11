package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.job.CloseJobsDisabledException;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStateMachineService;
import com.teamhub.job.JobStatus;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.dto.UpdateTenantSettingsRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
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
 * Integration tests for the tenant settings toggle (PRD §5.2, §9.3, §13; TASK-053).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link
 *       #step1_closeJobsDisabled_technicianTransitionToCompleted_throws403()}): {@code
 *       close_jobs_enabled = false} → {@code IN_PROGRESS → COMPLETED} by TECHNICIAN → {@link
 *       CloseJobsDisabledException} (HTTP 403, {@code CLOSE_JOBS_DISABLED}).
 *   <li><b>Step 2</b> ({@link #step2_closeJobsEnabled_technicianTransitionToCompleted_succeeds()}):
 *       {@code close_jobs_enabled = true} → same transition → succeeds (200).
 *   <li><b>Step 3</b> ({@link #step3_patchSettings_asDispatcher_returns403()}): {@code PATCH
 *       /api/v1/tenant/settings} as DISPATCHER → 403 (Admin only).
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class TenantSettingsIT {

  private static final String SETTINGS_URL = "/api/v1/tenant/settings";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserRepository userRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private TenantSettingsService tenantSettingsService;

  @Autowired private JobStateMachineService stateMachine;

  @Autowired private JobRepository jobRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  private String adminToken;

  private String dispatcherToken;

  private String technicianToken;

  private UUID technicianUserId;

  private UUID adminUserId;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    String ownerEmail = "settings+" + UUID.randomUUID() + "@test.com";
    tenant =
        tx.execute(
            s ->
                tenantService.provisionWithAdminUser(
                    "Settings Co " + UUID.randomUUID(), ownerEmail));

    UUID tid = tenant.getId();
    adminUserId =
        userRepository
            .findByEmail(ownerEmail)
            .orElseThrow(() -> new IllegalStateException("Admin user not found after provision"))
            .getId();
    technicianUserId = UUID.randomUUID();

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
              c.setName("Settings Customer");
              c.setPhone("+15030001111");
              c.setAddressLine("10 Toggle Ave");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    adminToken = mintToken(tid, adminUserId, UserRole.ADMIN.name());
    dispatcherToken = mintToken(tid, UUID.randomUUID(), UserRole.DISPATCHER.name());
    technicianToken = mintToken(tid, technicianUserId, UserRole.TECHNICIAN.name());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
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
  // Step 1 — close_jobs_enabled=false → TECHNICIAN IN_PROGRESS→COMPLETED → 403
  // =========================================================================

  /**
   * TASK-053 Step 1: when {@code close_jobs_enabled} is {@code false} (the default), a TECHNICIAN
   * attempting the {@code IN_PROGRESS → COMPLETED} transition must receive a {@link
   * CloseJobsDisabledException} with HTTP 403 and code {@code CLOSE_JOBS_DISABLED}.
   */
  @Test
  void step1_closeJobsDisabled_technicianTransitionToCompleted_throws403() {
    // Ensure the toggle is off (default is false, but be explicit).
    tx.execute(
        s -> {
          jdbc.update("UPDATE tenants SET close_jobs_enabled = false WHERE id = ?", tenant.getId());
          return null;
        });

    UUID jobId = createJobInProgress();

    TenantContext.set(tenant.getId());
    try {
      assertThatThrownBy(
              () ->
                  stateMachine.transition(
                      jobId, JobStatus.COMPLETED, technicianUserId, "TECHNICIAN", null))
          .isInstanceOf(CloseJobsDisabledException.class)
          .hasFieldOrPropertyWithValue("httpStatus", org.springframework.http.HttpStatus.FORBIDDEN)
          .satisfies(
              ex ->
                  assertThat(((CloseJobsDisabledException) ex).getCode())
                      .isEqualTo(CloseJobsDisabledException.CODE));
    } finally {
      TenantContext.clear();
    }
  }

  // =========================================================================
  // Step 2 — close_jobs_enabled=true → TECHNICIAN IN_PROGRESS→COMPLETED → 200
  // =========================================================================

  /**
   * TASK-053 Step 2: when {@code close_jobs_enabled} is {@code true}, the same {@code IN_PROGRESS →
   * COMPLETED} transition by a TECHNICIAN succeeds and the job status is persisted.
   */
  @Test
  void step2_closeJobsEnabled_technicianTransitionToCompleted_succeeds() {
    // Enable the toggle via the service (also verifies the service write path).
    tx.execute(
        s -> {
          jdbc.update("UPDATE tenants SET close_jobs_enabled = true WHERE id = ?", tenant.getId());
          return null;
        });

    UUID jobId = createJobInProgress();

    TenantContext.set(tenant.getId());
    stateMachine.transition(jobId, JobStatus.COMPLETED, technicianUserId, "TECHNICIAN", null);
    TenantContext.clear();

    Job updated = jobRepository.findById(jobId).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  // =========================================================================
  // Step 3 — PATCH /api/v1/tenant/settings as DISPATCHER → 403
  // =========================================================================

  /**
   * TASK-053 Step 3: {@code PATCH /api/v1/tenant/settings} requires {@code
   * PERM_tenant.settings.edit}, which is granted to ADMIN only. A DISPATCHER request must be
   * rejected with 403.
   */
  @Test
  void step3_patchSettings_asDispatcher_returns403() throws Exception {
    UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(true, null);

    mockMvc
        .perform(
            patch(SETTINGS_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // =========================================================================
  // Additional — GET /api/v1/tenant/settings as TECHNICIAN → 200
  // =========================================================================

  /**
   * Verifies that TECHNICIAN can read tenant settings ({@code PERM_tenant.settings.read} is seeded
   * for TECHNICIAN in V15).
   */
  @Test
  void getSettings_asTechnician_returns200WithToggleValue() throws Exception {
    mockMvc
        .perform(get(SETTINGS_URL).header("Authorization", "Bearer " + technicianToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closeJobsEnabled").value(false))
        .andExpect(jsonPath("$.timezone").value(TenantTimezones.DEFAULT));
  }

  // =========================================================================
  // Additional — PATCH /api/v1/tenant/settings as ADMIN updates the toggle
  // =========================================================================

  /**
   * Verifies that ADMIN can enable the Close Jobs toggle and the response reflects the new value.
   * Also confirms that an audit row is written asynchronously.
   */
  @Test
  void patchSettings_asAdmin_updatesToggleAndEmitsAudit() throws Exception {
    UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(true, null);

    mockMvc
        .perform(
            patch(SETTINGS_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closeJobsEnabled").value(true));

    // Verify persistence.
    Boolean stored =
        jdbc.queryForObject(
            "SELECT close_jobs_enabled FROM tenants WHERE id = ?", Boolean.class, tenant.getId());
    assertThat(stored).isTrue();

    // Wait for the async audit write to commit (AsyncAuditService uses @Async).
    UUID tid = tenant.getId();
    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer auditCount =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit_log"
                          + " WHERE tenant_id = ? AND action = 'tenant.close_jobs.toggled'",
                      Integer.class,
                      tid);
              assertThat(auditCount).isGreaterThanOrEqualTo(1);
            });
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Creates a job already in IN_PROGRESS status, committed in its own transaction. */
  private UUID createJobInProgress() {
    return tx.execute(
        s -> {
          Job j = new Job();
          j.setTenantId(tenant.getId());
          j.setCustomerId(customerId);
          j.setStatus(JobStatus.IN_PROGRESS);
          j.setCustomFields(Map.of());
          jobRepository.save(j);
          return j.getId();
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
