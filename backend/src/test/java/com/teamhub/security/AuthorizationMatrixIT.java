package com.teamhub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStatus;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * PRD §5.6 Authorization Matrix — CI integration tests (TASK-070).
 *
 * <p>All six test cases run against a real PostgreSQL Testcontainer with Row-Level Security
 * enabled. Each test provisions isolated tenants and cleans up in {@link #tearDown()}.
 *
 * <h2>RLS vs. {@code @PreAuthorize} enforcement distinction</h2>
 *
 * <ul>
 *   <li><strong>Tests 1 and 4</strong> rely on <em>application-level</em> enforcement:
 *       {@code @PreAuthorize} method security and the service-layer TECHNICIAN ownership check in
 *       {@code JobService.getById()}. RLS does <em>not</em> help here because both the caller and
 *       the target resource share the same {@code tenant_id} — RLS enforces only cross-tenant
 *       isolation.
 *   <li><strong>Tests 2, 5, and 6</strong> rely on <em>database-level</em> Row-Level Security.
 *       Without RLS, the {@code jobs} table would be fully readable across tenants if an attacker
 *       supplied a forged {@code tenant_id} GUC or if application-level filters were bypassed. RLS
 *       provides the final, unconditional enforcement layer at the database row level: a query
 *       running as {@code teamhub_app} with GUC {@code app.current_tenant_id = tenantB} will never
 *       see rows where {@code jobs.tenant_id = tenantA}, regardless of any JPA predicate. The
 *       application-level tenant filter in the list/getById methods is defence-in-depth.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationMatrixIT {

  private static final String JOBS_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private JobRepository jobRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private PlatformTransactionManager txManager;

  /** Tenant IDs provisioned during a test — cleaned up in tearDown. */
  private final java.util.Set<UUID> provisionedTenantIds = new java.util.LinkedHashSet<>();

  @AfterEach
  void tearDown() {
    for (UUID tid : provisionedTenantIds) {
      jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
    }
    provisionedTenantIds.clear();
  }

  // =========================================================================
  // Test 1 — TECHNICIAN cannot read another technician's job in the same tenant
  // Enforcement: application-level check in JobService.getById() — NOT RLS.
  // RLS allows the row (same tenant_id), but the service throws
  // JobNotAssignedToCallerException (403) because tech2 is not the assignee.
  // =========================================================================

  /**
   * PRD §5.6 matrix row 1: a TECHNICIAN may only read ({@code job.read.own}) jobs assigned to
   * themselves or in SUBMITTED (shared-pool) status. Reading a job assigned to another technician
   * within the same tenant must return 403.
   *
   * <p>Enforcement: {@code JobService.getById()} ownership check — not RLS (same tenant).
   */
  @Test
  void tech_cannotRead_anotherTechniciansJob_sameTenant() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenant = tx.execute(s -> provisionTenant("SameTenant Co"));
    UUID tid = tenant.getId();

    UUID tech1Id = UUID.randomUUID();
    UUID tech2Id = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              tech1Id,
              tid,
              "tech1-" + tech1Id + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              tech2Id,
              tid,
              "tech2-" + tech2Id + "@test.com");
          return null;
        });

    UUID customerId = createCustomer(tx, tid);

    // Create a job assigned specifically to tech1.
    UUID jobId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tid);
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.ASSIGNED_TO_TECHNICIAN);
              j.setAssignedTechnicianId(tech1Id);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    // Tech2 tries to read tech1's job.  Same tenant → RLS passes; service must deny.
    String tech2Token = mintToken(tid, tech2Id, "TECHNICIAN");
    mockMvc
        .perform(get(JOBS_URL + "/" + jobId).header("Authorization", "Bearer " + tech2Token))
        .andExpect(status().isForbidden());
  }

  // =========================================================================
  // Test 2 — TECHNICIAN cannot read a job from another tenant
  // Enforcement: PostgreSQL RLS (tenant_isolation policy on jobs).
  // Without RLS the job row in tenant A would be visible to any connection
  // that queries with jobId; with RLS it is filtered out because the GUC
  // (app.current_tenant_id) is set to tenant B's id, so the row is invisible
  // and the service throws JobNotFoundException (404).
  // =========================================================================

  /**
   * PRD §5.6 matrix row 2: a TECHNICIAN with a valid token for tenant B cannot see jobs belonging
   * to tenant A. RLS filters the row at the database level → 404 (row invisible).
   *
   * <p>Enforcement: PostgreSQL Row-Level Security ({@code tenant_isolation} policy on {@code
   * jobs}). Without RLS this test would return 200 with tenant A's job data.
   */
  @Test
  void tech_cannotRead_jobFromOtherTenant() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenantA = tx.execute(s -> provisionTenant("TenantA Plumbing"));
    Tenant tenantB = tx.execute(s -> provisionTenant("TenantB Electric"));

    UUID customerId = createCustomer(tx, tenantA.getId());
    UUID jobAId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tenantA.getId());
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    UUID techBId = UUID.randomUUID();
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              techBId,
              tenantB.getId(),
              "techB-" + techBId + "@test.com");
          return null;
        });

    // Tech-B calls GET /api/v1/jobs/{tenantA_jobId} with tenant B's token.
    // RLS hides the row (GUC = tenantB ≠ job.tenant_id = tenantA) → 404.
    String techBToken = mintToken(tenantB.getId(), techBId, "TECHNICIAN");
    mockMvc
        .perform(get(JOBS_URL + "/" + jobAId).header("Authorization", "Bearer " + techBToken))
        .andExpect(status().isNotFound());
  }

  // =========================================================================
  // Test 3 — TECHNICIAN cannot complete a job when close_jobs_enabled = false
  // Enforcement: application-level CloseJobsDisabledException (HTTP 403,
  // code CLOSE_JOBS_DISABLED) — not RLS (same tenant).
  // =========================================================================

  /**
   * PRD §5.6 matrix row 3: when {@code close_jobs_enabled = false} (the default), a TECHNICIAN
   * attempting {@code POST /api/v1/jobs/{id}/complete} must receive 403 with code {@code
   * CLOSE_JOBS_DISABLED}.
   *
   * <p>Enforcement: {@code JobStateMachineService} runtime toggle check — not RLS.
   */
  @Test
  void tech_cannotComplete_whenCloseJobsDisabled() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenant = tx.execute(s -> provisionTenant("NoCloseJobs Co"));
    UUID tid = tenant.getId();

    // Ensure the toggle is explicitly off.
    tx.execute(
        s -> {
          jdbc.update("UPDATE tenants SET close_jobs_enabled = false WHERE id = ?", tid);
          return null;
        });

    UUID techId = UUID.randomUUID();
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              techId,
              tid,
              "tech3-" + techId + "@test.com");
          return null;
        });

    UUID customerId = createCustomer(tx, tid);

    // Job in IN_PROGRESS, assigned to the technician.
    UUID jobId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tid);
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.IN_PROGRESS);
              j.setAssignedTechnicianId(techId);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    String techToken = mintToken(tid, techId, "TECHNICIAN");
    mockMvc
        .perform(
            post(JOBS_URL + "/" + jobId + "/complete")
                .header("Authorization", "Bearer " + techToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CLOSE_JOBS_DISABLED"));
  }

  // =========================================================================
  // Test 4 — DISPATCHER cannot hard-delete a job
  // Enforcement: @PreAuthorize("hasAuthority('PERM_job.hard_delete')") — not RLS.
  // DISPATCHER has PERM_job.delete (soft-archive) but NOT PERM_job.hard_delete
  // (ADMIN only). Spring Security denies the request before it reaches the service.
  // =========================================================================

  /**
   * PRD §5.6 matrix row 4: the hard-delete endpoint is guarded by {@code PERM_job.hard_delete}
   * (ADMIN only). A DISPATCHER request must be rejected with 403 before the service layer is
   * invoked.
   *
   * <p>Enforcement: {@code @PreAuthorize("hasAuthority('PERM_job.hard_delete')")} — not RLS.
   */
  @Test
  void dispatcher_cannotHardDelete_job() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenant = tx.execute(s -> provisionTenant("HardDelete Co"));
    UUID tid = tenant.getId();

    UUID customerId = createCustomer(tx, tid);
    UUID jobId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tid);
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    UUID dispatcherId = UUID.randomUUID();
    String dispatcherToken = mintToken(tid, dispatcherId, "DISPATCHER");

    mockMvc
        .perform(
            delete(JOBS_URL + "/" + jobId + "/hard-delete")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // =========================================================================
  // Test 5 — DISPATCHER cannot read a job from another tenant
  // Enforcement: PostgreSQL RLS (same tenant_isolation policy).
  // Without RLS, dispatcher-B with a crafted token for tenantB could retrieve
  // a job whose tenant_id = tenantA if the application filter were bypassed.
  // With RLS, the row is invisible at the database layer → 404.
  // =========================================================================

  /**
   * PRD §5.6 matrix row 5: a DISPATCHER with a valid token for tenant B cannot see jobs belonging
   * to tenant A. RLS filters the row at the database level → 404 (row invisible).
   *
   * <p>Enforcement: PostgreSQL Row-Level Security — not {@code @PreAuthorize}. Without RLS this
   * test would return 200 with tenant A's job data.
   */
  @Test
  void dispatcher_cannotRead_jobFromOtherTenant() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenantA = tx.execute(s -> provisionTenant("DispTenantA Plumbing"));
    Tenant tenantB = tx.execute(s -> provisionTenant("DispTenantB Electric"));

    UUID customerId = createCustomer(tx, tenantA.getId());
    UUID jobAId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tenantA.getId());
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    UUID dispatcherBId = UUID.randomUUID();
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'DISPATCHER')",
              dispatcherBId,
              tenantB.getId(),
              "dispB-" + dispatcherBId + "@test.com");
          return null;
        });

    // Dispatcher-B calls GET /api/v1/jobs/{tenantA_jobId} with tenant B's token.
    // RLS hides the row (GUC = tenantB ≠ job.tenant_id = tenantA) → 404.
    String dispatcherBToken = mintToken(tenantB.getId(), dispatcherBId, "DISPATCHER");
    mockMvc
        .perform(get(JOBS_URL + "/" + jobAId).header("Authorization", "Bearer " + dispatcherBToken))
        .andExpect(status().isNotFound());
  }

  // =========================================================================
  // Test 6 — ADMIN from tenant A cannot read tenant B's jobs via list endpoint
  // Enforcement: PostgreSQL RLS scopes the list query to GUC = tenantA,
  // so tenant B's rows are never returned by the database, even if the JPA
  // Specification filter were somehow omitted. Both the application filter
  // (cb.equal("tenantId", tenantA)) and RLS enforce isolation; RLS is the
  // unconditional last line of defence.
  // =========================================================================

  /**
   * PRD §5.6 matrix row 6: when admin-A calls {@code GET /api/v1/jobs}, the paginated result must
   * contain only tenant A's jobs. Tenant B's jobs must be absent regardless of any JPA filter.
   *
   * <p>Enforcement: PostgreSQL Row-Level Security (the {@code tenant_isolation} policy on {@code
   * jobs}) combined with the application-level tenant predicate in {@code JobService.list()}.
   * Without RLS, a compromised or missing JPA filter could leak tenant B's rows.
   */
  @Test
  void adminA_cannotRead_tenantB_jobs() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    Tenant tenantA = tx.execute(s -> provisionTenant("AdminA Plumbing"));
    Tenant tenantB = tx.execute(s -> provisionTenant("AdminB Electric"));

    UUID customerA = createCustomer(tx, tenantA.getId());
    UUID customerB = createCustomer(tx, tenantB.getId());

    UUID jobAId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tenantA.getId());
              j.setCustomerId(customerA);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    UUID jobBId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tenantB.getId());
              j.setCustomerId(customerB);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    // The admin's userId matches the user provisioned via InternalTenantService.
    UUID adminAId =
        UUID.fromString(
            jdbc.queryForObject(
                "SELECT id FROM users WHERE tenant_id = ? AND role = 'ADMIN'",
                String.class,
                tenantA.getId()));

    String adminAToken = mintToken(tenantA.getId(), adminAId, "ADMIN");

    MvcResult result =
        mockMvc
            .perform(get(JOBS_URL).header("Authorization", "Bearer " + adminAToken))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    JsonNode root = objectMapper.readTree(responseBody);
    ArrayNode content = (ArrayNode) root.get("content");

    java.util.List<String> returnedIds = new java.util.ArrayList<>();
    content.forEach(jobNode -> returnedIds.add(jobNode.get("id").asText()));

    // Tenant A's job must be visible.
    assertThat(returnedIds).contains(jobAId.toString());

    // Tenant B's job must NOT appear — RLS enforces the isolation.
    assertThat(returnedIds).doesNotContain(jobBId.toString());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private Tenant provisionTenant(String name) {
    String email = "admin-" + UUID.randomUUID() + "@matrix-test.com";
    Tenant t = tenantService.provision(name + " " + UUID.randomUUID(), email);
    provisionedTenantIds.add(t.getId());
    return t;
  }

  private UUID createCustomer(TransactionTemplate tx, UUID tenantId) {
    return tx.execute(
        s -> {
          Customer c = new Customer();
          c.setTenantId(tenantId);
          c.setName("Matrix Customer");
          c.setPhone("+15030009999");
          c.setAddressLine("1 Matrix Ave");
          c.setCity("Portland");
          c.setState("OR");
          c.setZip("97201");
          c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
          customerRepository.save(c);
          return c.getId();
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
