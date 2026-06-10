package com.teamhub.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.billing.PlanFeatureLockedException;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * Integration tests for Admin/Dispatcher report endpoints (PRD §18.1, §18.2, TASK-067).
 *
 * <ul>
 *   <li><b>Step 1</b>: Insert 10 jobs with varying statuses — {@code GET /reports/jobs?period=day}
 *       returns correct counts.
 *   <li><b>Step 2</b>: Free tenant calls {@code GET /reports/per-technician} → 402 {@code
 *       PLAN_FEATURE_LOCKED}.
 *   <li><b>Step 3</b>: Call {@code GET /reports/jobs} twice; assert Redis cache key exists after
 *       the first call.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class ReportIT {

  private static final String BASE_URL = "/api/v1/reports";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  private UUID adminUserId;

  private UUID technicianId;

  private String adminToken;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);

    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Report Corp " + UUID.randomUUID(),
                    "report+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();

    // Retrieve the seeded admin user id.
    adminUserId =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE tenant_id = ?::uuid LIMIT 1", UUID.class, tid.toString());

    // Create a technician user for per-technician tests.
    technicianId = UUID.randomUUID();
    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role) VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              technicianId.toString(),
              tid.toString(),
              "tech-" + technicianId + "@test.com");
          return null;
        });

    // Create a customer.
    Customer customer = new Customer();
    customer.setTenantId(tid);
    customer.setName("Report Customer");
    customer.setPhone("+15033334444");
    customer.setAddressLine("1 Report St");
    customer.setCity("Portland");
    customer.setState("OR");
    customer.setZip("97201");
    customer.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
    customerRepository.save(customer);
    customerId = customer.getId();

    adminToken = mintToken(tid, adminUserId, UserRole.ADMIN.name());
    dispatcherToken = mintToken(tid, UUID.randomUUID(), UserRole.DISPATCHER.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM users WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM tenants WHERE id = ?::uuid", tid.toString());
          return null;
        });
    // Clean Redis keys created during the test.
    var keys = redisTemplate.keys("reports:totals:" + tenant.getId() + ":*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  // ---------------------------------------------------------------------------
  // Step 1: Insert 10 jobs — GET /reports/jobs?period=day returns correct counts
  // ---------------------------------------------------------------------------

  /**
   * TASK-067 Step 1: Insert 10 jobs (6 SUBMITTED/created today, 3 COMPLETED today, 1 CANCELED
   * today). The daily report must return a single bucket with created=10, completed=3, cancelled=1.
   */
  @Test
  void step1_jobCounts_byDay_returnsCorrectCounts() throws Exception {
    UUID tid = tenant.getId();
    String today = java.time.LocalDate.now().toString();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          // 6 SUBMITTED jobs
          for (int i = 0; i < 6; i++) {
            jdbc.update(
                "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start, custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', ?::timestamptz, '{}', 0)",
                tid.toString(),
                customerId.toString(),
                scheduledStart);
          }
          // 3 COMPLETED jobs
          for (int i = 0; i < 3; i++) {
            jdbc.update(
                "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start, custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, 'COMPLETED', ?::timestamptz, '{}', 0)",
                tid.toString(),
                customerId.toString(),
                scheduledStart);
          }
          // 1 CANCELED job
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start, custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, 'CANCELED', ?::timestamptz, '{}', 0)",
              tid.toString(),
              customerId.toString(),
              scheduledStart);
          return null;
        });

    mockMvc
        .perform(
            get(BASE_URL + "/jobs")
                .param("period", "day")
                .param("from", today)
                .param("to", today)
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buckets").isArray())
        .andExpect(jsonPath("$.buckets[0].created").value(10))
        .andExpect(jsonPath("$.buckets[0].completed").value(3))
        .andExpect(jsonPath("$.buckets[0].cancelled").value(1));
  }

  // ---------------------------------------------------------------------------
  // Step 2: Free tenant → GET /reports/per-technician → 402 PLAN_FEATURE_LOCKED
  // ---------------------------------------------------------------------------

  /**
   * TASK-067 Step 2: Free tenant calls {@code GET /reports/per-technician} — must receive 402 with
   * error code {@code PLAN_FEATURE_LOCKED}.
   */
  @Test
  void step2_freeTenant_perTechnician_returns402() throws Exception {
    tenant.setPlan(Plan.FREE);
    tenantRepository.saveAndFlush(tenant);

    mockMvc
        .perform(get(BASE_URL + "/per-technician").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isPaymentRequired())
        .andExpect(jsonPath("$.error.code").value(PlanFeatureLockedException.CODE));
  }

  // ---------------------------------------------------------------------------
  // Step 3: Redis cache key exists after first GET /reports/jobs?period=month call
  // ---------------------------------------------------------------------------

  /**
   * TASK-067 Step 3: First call to monthly job counts populates the Redis cache. Second call hits
   * the cache (key exists with TTL &gt; 0).
   */
  @Test
  void step3_monthlyJobCounts_populatesRedisCache() throws Exception {
    String thisMonth = java.time.YearMonth.now().toString();
    String cacheKey = "reports:totals:" + tenant.getId() + ":" + thisMonth;
    String monthStart = java.time.YearMonth.now().atDay(1).toString();
    String monthEnd = java.time.YearMonth.now().atEndOfMonth().toString();

    // Ensure key is absent before the test.
    redisTemplate.delete(cacheKey);

    // First call — cache miss.
    mockMvc
        .perform(
            get(BASE_URL + "/jobs")
                .param("period", "month")
                .param("from", monthStart)
                .param("to", monthEnd)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    // Cache key must now exist.
    assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

    // Second call — cache hit; still returns 200.
    mockMvc
        .perform(
            get(BASE_URL + "/jobs")
                .param("period", "month")
                .param("from", monthStart)
                .param("to", monthEnd)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Additional: GET /reports/avg-times endpoint coverage
  // ---------------------------------------------------------------------------

  @Test
  void avgTimes_withExplicitDates_returnsOk() throws Exception {
    String today = java.time.LocalDate.now().toString();

    mockMvc
        .perform(
            get(BASE_URL + "/avg-times")
                .param("from", today)
                .param("to", today)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void avgTimes_noParams_usesDefaults() throws Exception {
    mockMvc
        .perform(get(BASE_URL + "/avg-times").header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk());
  }

  @Test
  void getJobCounts_noParams_usesDefaultDateRange() throws Exception {
    mockMvc
        .perform(get(BASE_URL + "/jobs").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void perTechnician_noParams_usesDefaultDateRange() throws Exception {
    tenant.setPlan(com.teamhub.tenancy.Plan.TEAM);
    tenantRepository.saveAndFlush(tenant);

    mockMvc
        .perform(get(BASE_URL + "/per-technician").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Additional: 403 for TECHNICIAN role
  // ---------------------------------------------------------------------------

  @Test
  void technician_getJobCounts_returns403() throws Exception {
    String techToken = mintToken(tenant.getId(), technicianId, UserRole.TECHNICIAN.name());

    mockMvc
        .perform(get(BASE_URL + "/jobs").header("Authorization", "Bearer " + techToken))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Additional: Team+ tenant can call per-technician
  // ---------------------------------------------------------------------------

  @Test
  void step2b_teamTenant_perTechnician_returns200() throws Exception {
    tenant.setPlan(Plan.TEAM);
    tenantRepository.saveAndFlush(tenant);

    mockMvc
        .perform(get(BASE_URL + "/per-technician").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

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
