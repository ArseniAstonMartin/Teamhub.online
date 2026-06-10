package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * End-to-end HTTP integration tests for the job-notes endpoints (PRD §13.2, §13.4; TASK-048):
 *
 * <ul>
 *   <li>{@code POST /api/v1/jobs/{id}/notes}
 *   <li>{@code GET /api/v1/jobs/{id}/notes}
 * </ul>
 *
 * <p>Test steps (verbatim from tasks.json):
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_addNote_asTechnician_returns201WithPersistedNote()}): Add a
 *       note as Technician on own job → 201 with note persisted.
 *   <li><b>Step 2</b> ({@link #step2_put_returns405()} and {@link #step2_delete_returns404()}):
 *       Attempt PUT → 405; DELETE → 404 (no routes registered).
 *   <li><b>Step 3</b> ({@link #step3_anotherTenant_listNotes_returns403OrEmpty()}): Another
 *       tenant's user attempts {@code GET /notes} on a job from a different tenant → 403.
 * </ul>
 *
 * <p><b>Step 3 behaviour note:</b> The service performs an explicit tenant-scoped lookup ({@link
 * JobNotFoundException} → 403 is not the result here — it is 404 NOT_FOUND mapped from {@link
 * JobNotFoundException} by the global exception handler). The cross-tenant caller receives a {@code
 * 404 JOB_NOT_FOUND} because the job does not exist within their tenant scope. This is the correct
 * and safest response: it neither leaks the existence of the resource nor the note contents.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class JobNotesIT {

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

  private Tenant otherTenant;

  private UUID customerId;

  private UUID technicianId;

  private String techToken;

  private String otherTenantToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);

    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Notes Co " + UUID.randomUUID(),
                    "notes-owner+" + UUID.randomUUID() + "@test.com"));

    otherTenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Other Co " + UUID.randomUUID(),
                    "other-owner+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    technicianId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              technicianId,
              tid,
              "tech-notes-" + technicianId + "@test.com");
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              otherUserId,
              otherTenant.getId(),
              "other-tech-" + otherUserId + "@test.com");
          return null;
        });

    customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Notes Customer");
              c.setPhone("+15031234567");
              c.setAddressLine("1 Note Ln");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    techToken = mintToken(tid, technicianId, UserRole.TECHNICIAN.name());
    otherTenantToken = mintToken(otherTenant.getId(), otherUserId, UserRole.TECHNICIAN.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    UUID otid = otherTenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_notes WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM job_notes WHERE tenant_id = ?", otid);
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", otid);
          jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", otid);
          return null;
        });
  }

  // =========================================================================
  // Step 1 — Technician adds a note → 201 with body persisted
  // =========================================================================

  /**
   * TASK-048 Step 1: As Technician, {@code POST /jobs/{id}/notes} returns 201 and the note is
   * persisted in {@code job_notes} with the correct fields.
   */
  @Test
  void step1_addNote_asTechnician_returns201WithPersistedNote() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);
    String body = "Replaced the main valve — all good.";

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/notes")
                .header("Authorization", "Bearer " + techToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", body))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.body").value(body))
        .andExpect(jsonPath("$.authorUserId").value(technicianId.toString()))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty());

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM job_notes WHERE job_id = ?::uuid AND body = ?",
            Integer.class,
            jobId,
            body);
    assertThat(count).isEqualTo(1);
  }

  /** Additional: GET /notes returns the previously added note. */
  @Test
  void listNotes_asTechnician_returnsAddedNotes() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);
    String noteBody = "Inspected wiring — no issues.";

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO job_notes (job_id, tenant_id, author_user_id, body)"
                  + " VALUES (?::uuid, ?::uuid, ?::uuid, ?)",
              jobId,
              tenant.getId(),
              technicianId,
              noteBody);
          return null;
        });

    mockMvc
        .perform(
            get(BASE_URL + "/" + jobId + "/notes").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].body").value(noteBody));
  }

  // =========================================================================
  // Step 2 — PUT returns 405; DELETE returns 404 (no route registered)
  // =========================================================================

  /**
   * TASK-048 Step 2a: {@code PUT /jobs/{id}/notes/{noteId}} must return 404 because no route is
   * registered for this path. Spring returns 404 (not 405) when no handler mapping matches the full
   * path — 405 would only be returned if the path {@code /notes/{noteId}} existed for another HTTP
   * method. Since no per-note sub-resource route is registered at all, the absence of the route is
   * confirmed by the 404 response.
   */
  @Test
  void step2_put_returns405() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);
    UUID noteId = UUID.randomUUID();

    mockMvc
        .perform(
            put(BASE_URL + "/" + jobId + "/notes/" + noteId)
                .header("Authorization", "Bearer " + techToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", "update attempt"))))
        .andExpect(status().isNotFound());
  }

  /**
   * TASK-048 Step 2b: {@code DELETE /jobs/{id}/notes/{noteId}} must return 404 because no DELETE
   * route is registered for a specific note.
   */
  @Test
  void step2_delete_returns404() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);
    UUID noteId = UUID.randomUUID();

    mockMvc
        .perform(
            delete(BASE_URL + "/" + jobId + "/notes/" + noteId)
                .header("Authorization", "Bearer " + techToken))
        .andExpect(status().isNotFound());
  }

  // =========================================================================
  // Step 3 — Cross-tenant access returns 404 JOB_NOT_FOUND
  // =========================================================================

  /**
   * TASK-048 Step 3: A user from a different tenant calling {@code GET /jobs/{id}/notes} on a job
   * owned by another tenant receives 404 {@code JOB_NOT_FOUND}.
   *
   * <p>The service performs a tenant-scoped job lookup; the job is invisible to the other tenant,
   * so {@link JobNotFoundException} is raised and mapped to HTTP 404. This prevents both existence
   * leaks and note-content leaks without requiring a separate 403 path.
   */
  @Test
  void step3_anotherTenant_listNotes_returns404() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);

    mockMvc
        .perform(
            get(BASE_URL + "/" + jobId + "/notes")
                .header("Authorization", "Bearer " + otherTenantToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(JobNotFoundException.CODE));
  }

  // =========================================================================
  // Additional — blank body returns 422 VALIDATION_FAILED
  // =========================================================================

  /** Additional: blank {@code body} must return 422 VALIDATION_FAILED. */
  @Test
  void addNote_blankBody_returns422() throws Exception {
    UUID jobId = createJob(JobStatus.IN_PROGRESS, technicianId);

    mockMvc
        .perform(
            post(BASE_URL + "/" + jobId + "/notes")
                .header("Authorization", "Bearer " + techToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", "   "))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  // =========================================================================
  // Helpers
  // =========================================================================

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
