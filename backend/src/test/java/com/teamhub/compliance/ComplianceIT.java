package com.teamhub.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.audit.AuditLogRepository;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStatus;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for CCPA compliance endpoints (TASK-071, PRD §24.5).
 *
 * <ul>
 *   <li>Step 1: Admin exports data — ZIP contains expected JSON files; audit row recorded.
 *   <li>Step 2: Admin erases a customer — PII replaced, customer's jobs preserved, audit row
 *       recorded.
 *   <li>Step 3: Non-Admin (DISPATCHER) attempts both endpoints — 403.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class ComplianceIT {

  private static final String EXPORT_URL = "/api/v1/compliance/export";

  private static final String ERASE_URL = "/api/v1/compliance/erase-customer/";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JobRepository jobRepository;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private PlatformTransactionManager txManager;

  private final Set<UUID> provisionedTenantIds = new HashSet<>();

  @AfterEach
  void tearDown() {
    for (UUID tid : provisionedTenantIds) {
      jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
      jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
    }
    provisionedTenantIds.clear();
  }

  // =========================================================================
  // Step 1 — Admin GET /compliance/export → 200 with ZIP containing 4 files
  // =========================================================================

  @Test
  void admin_export_returns200_zipWithExpectedFiles_andAuditRowRecorded() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    Tenant tenant = provisionTenant("Compliance Export Co");
    UUID adminId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'ADMIN')",
              adminId,
              tenant.getId(),
              "admin-" + adminId + "@test.com");
          return null;
        });

    String token = mintToken(tenant.getId(), adminId, "ADMIN");

    MvcResult result =
        mockMvc
            .perform(get(EXPORT_URL).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body).isNotEmpty();

    Set<String> zipEntries = new HashSet<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        zipEntries.add(entry.getName());
        zis.closeEntry();
      }
    }
    assertThat(zipEntries).contains("users.json", "jobs.json", "customers.json", "audit_log.json");

    boolean auditExists =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM audit_log WHERE tenant_id = ?::uuid"
                    + " AND action = 'compliance.data_export')",
                Boolean.class,
                tenant.getId()));
    assertThat(auditExists).isTrue();
  }

  // =========================================================================
  // Step 2 — Admin erases customer; PII replaced, jobs preserved, audit row
  // =========================================================================

  @Test
  void admin_eraseCustomer_redactsPii_preservesJobs_andAuditRowRecorded() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    Tenant tenant = provisionTenant("Compliance Erase Co");
    UUID adminId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'ADMIN')",
              adminId,
              tenant.getId(),
              "admin-erase-" + adminId + "@test.com");
          return null;
        });

    UUID customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tenant.getId());
              c.setName("John Doe");
              c.setPhone("+15031234567");
              c.setEmail("john@example.com");
              c.setAddressLine("123 Main St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5051)));
              customerRepository.save(c);
              return c.getId();
            });

    UUID jobId =
        tx.execute(
            s -> {
              Job j = new Job();
              j.setTenantId(tenant.getId());
              j.setCustomerId(customerId);
              j.setStatus(JobStatus.SUBMITTED);
              j.setCustomFields(Map.of());
              jobRepository.save(j);
              return j.getId();
            });

    String token = mintToken(tenant.getId(), adminId, "ADMIN");

    mockMvc
        .perform(post(ERASE_URL + customerId).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    Customer erased =
        tx.execute(
            s -> customerRepository.findByIdAndTenantId(customerId, tenant.getId()).orElseThrow());

    assertThat(erased.getName()).isEqualTo("[REDACTED]");
    assertThat(erased.getEmail()).isEqualTo("[REDACTED]");
    assertThat(erased.getPhone()).isEqualTo("[REDACTED]");
    assertThat(erased.getArchivedAt()).isNotNull();

    Job linkedJob = tx.execute(s -> jobRepository.findById(jobId).orElseThrow());
    assertThat(linkedJob.getCustomerId()).isEqualTo(customerId);

    boolean auditExists =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM audit_log WHERE tenant_id = ?::uuid"
                    + " AND action = 'compliance.customer_erased'"
                    + " AND resource_id = ?::uuid)",
                Boolean.class,
                tenant.getId(),
                customerId));
    assertThat(auditExists).isTrue();
  }

  // =========================================================================
  // Step 3 — Non-Admin (DISPATCHER) gets 403 on both endpoints
  // =========================================================================

  @Test
  void dispatcher_export_returns403() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    Tenant tenant = provisionTenant("Compliance Forbidden Co");
    UUID dispatcherId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'DISPATCHER')",
              dispatcherId,
              tenant.getId(),
              "dispatcher-" + dispatcherId + "@test.com");
          return null;
        });

    String token = mintToken(tenant.getId(), dispatcherId, "DISPATCHER");

    mockMvc
        .perform(get(EXPORT_URL).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void dispatcher_eraseCustomer_returns403() throws Exception {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    Tenant tenant = provisionTenant("Compliance Forbidden Erase Co");
    UUID dispatcherId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'DISPATCHER')",
              dispatcherId,
              tenant.getId(),
              "dispatcher-erase-" + dispatcherId + "@test.com");
          return null;
        });

    String token = mintToken(tenant.getId(), dispatcherId, "DISPATCHER");

    mockMvc
        .perform(post(ERASE_URL + UUID.randomUUID()).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private Tenant provisionTenant(String name) {
    String email = "admin-" + UUID.randomUUID() + "@compliance-it.com";
    Tenant t = tenantService.provision(name + " " + UUID.randomUUID(), email);
    provisionedTenantIds.add(t.getId());
    return t;
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
