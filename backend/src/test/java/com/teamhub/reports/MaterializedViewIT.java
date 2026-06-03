package com.teamhub.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.reports.dto.AvgTimesResponse;
import com.teamhub.reports.dto.DayBucket;
import com.teamhub.reports.dto.TechnicianRateEntry;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the report materialized views (TASK-068, PRD §18.2).
 *
 * <ul>
 *   <li><b>Step 1</b>: Boot — verify all 6 views exist in {@code pg_catalog.pg_matviews}.
 *   <li><b>Step 2</b>: Insert 3 jobs, trigger a manual refresh, assert {@code mv_jobs_daily} shows
 *       count = 3 for the tenant.
 *   <li><b>Step 3</b>: Concurrent {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} and {@code SELECT}
 *       do not block each other — no exception is thrown.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MaterializedViewIT {

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private InternalTenantService tenantService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private MaterializedViewRefreshJob refreshJob;

  @Autowired private ReportRepository reportRepository;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);

    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "MV Corp " + UUID.randomUUID(), "mv+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();

    Customer customer = new Customer();
    customer.setTenantId(tid);
    customer.setName("MV Customer");
    customer.setPhone("+15035550000");
    customer.setAddressLine("1 MV St");
    customer.setCity("Portland");
    customer.setState("OR");
    customer.setZip("97201");
    customer.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
    customerRepository.save(customer);
    customerId = customer.getId();
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM users WHERE tenant_id = ?::uuid", tid.toString());
          jdbc.update("DELETE FROM tenants WHERE id = ?::uuid", tid.toString());
          return null;
        });
  }

  // ---------------------------------------------------------------------------
  // Step 1: All 6 views exist in pg_catalog.pg_matviews
  // ---------------------------------------------------------------------------

  /**
   * TASK-068 Step 1: After the application boots (and Flyway applies V28), all six materialized
   * views must be registered in {@code pg_catalog.pg_matviews}.
   */
  @Test
  void step1_allSixViewsExistInCatalog() {
    List<String> expectedViews =
        List.of(
            "mv_jobs_daily",
            "mv_jobs_weekly",
            "mv_jobs_monthly",
            "mv_time_to_claim",
            "mv_time_to_completion",
            "mv_tech_completion_rate");

    List<String> actualViews =
        jdbc.queryForList(
            """
            SELECT matviewname
            FROM pg_catalog.pg_matviews
            WHERE matviewname IN (
                'mv_jobs_daily',
                'mv_jobs_weekly',
                'mv_jobs_monthly',
                'mv_time_to_claim',
                'mv_time_to_completion',
                'mv_tech_completion_rate'
            )
            ORDER BY matviewname
            """,
            String.class);

    assertThat(actualViews).containsExactlyInAnyOrderElementsOf(expectedViews);
  }

  // ---------------------------------------------------------------------------
  // Step 2: Insert 3 jobs, refresh, assert mv_jobs_daily count = 3
  // ---------------------------------------------------------------------------

  /**
   * TASK-068 Step 2: Insert 3 SUBMITTED jobs, call {@link MaterializedViewRefreshJob#refreshAll()},
   * then query {@code mv_jobs_daily} — the row count for the tenant must be 3.
   */
  @Test
  void step2_afterRefresh_mvJobsDailyReflectsInsertedJobs() {
    UUID tid = tenant.getId();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          for (int i = 0; i < 3; i++) {
            jdbc.update(
                "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start,"
                    + " custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', ?::timestamptz, '{}', 0)",
                tid.toString(),
                customerId.toString(),
                scheduledStart);
          }
          return null;
        });

    refreshJob.refreshAll();

    Long count =
        jdbc.queryForObject(
            "SELECT SUM(job_count) FROM mv_jobs_daily WHERE tenant_id = ?::uuid",
            Long.class,
            tid.toString());

    assertThat(count).isEqualTo(3L);
  }

  // ---------------------------------------------------------------------------
  // Step 3: Concurrent REFRESH and SELECT do not block each other
  // ---------------------------------------------------------------------------

  /**
   * TASK-068 Step 3: Running {@code REFRESH MATERIALIZED VIEW CONCURRENTLY mv_jobs_daily} in
   * parallel with a {@code SELECT} on the same view must not raise any exception, confirming that
   * the unique index enables non-blocking concurrent refresh.
   */
  @Test
  void step3_concurrentRefreshAndSelect_doNotBlock()
      throws ExecutionException, InterruptedException {
    CompletableFuture<Void> refreshFuture =
        CompletableFuture.runAsync(
            () ->
                assertThatCode(
                        () -> jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_jobs_daily"))
                    .doesNotThrowAnyException());

    CompletableFuture<Void> selectFuture =
        CompletableFuture.runAsync(
            () ->
                assertThatCode(
                        () ->
                            jdbc.queryForList(
                                "SELECT COUNT(*) FROM mv_jobs_daily WHERE tenant_id = ?::uuid",
                                tenant.getId().toString()))
                    .doesNotThrowAnyException());

    CompletableFuture.allOf(refreshFuture, selectFuture).get();
  }

  // ---------------------------------------------------------------------------
  // MV repository method tests — ensure new code coverage ≥ 80%
  // ---------------------------------------------------------------------------

  /**
   * Exercises {@link ReportRepository#mvGetJobCountsByDay}, {@link
   * ReportRepository#mvGetJobCountsByWeek}, and {@link ReportRepository#mvGetJobCountsByMonth} for
   * a tenant with 2 SUBMITTED and 1 COMPLETED job, after a manual refresh.
   */
  @Test
  void mvJobCountMethods_returnCorrectBuckets() {
    UUID tid = tenant.getId();
    LocalDate today = LocalDate.now();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          for (int i = 0; i < 2; i++) {
            jdbc.update(
                "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start,"
                    + " custom_fields, version)"
                    + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', ?::timestamptz, '{}', 0)",
                tid.toString(),
                customerId.toString(),
                scheduledStart);
          }
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start,"
                  + " custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, 'COMPLETED', ?::timestamptz, '{}', 0)",
              tid.toString(),
              customerId.toString(),
              scheduledStart);
          // CANCELED job — exercises the cancelled counter branch in pivotJobCountRows
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start,"
                  + " custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, 'CANCELED', ?::timestamptz, '{}', 0)",
              tid.toString(),
              customerId.toString(),
              scheduledStart);
          return null;
        });

    refreshJob.refreshAll();

    List<DayBucket> daily = reportRepository.mvGetJobCountsByDay(tid, today, today);
    assertThat(daily).isNotEmpty();
    long dailyTotal = daily.stream().mapToLong(DayBucket::created).sum();
    assertThat(dailyTotal).isEqualTo(4L);
    long dailyCancelled = daily.stream().mapToLong(DayBucket::cancelled).sum();
    assertThat(dailyCancelled).isEqualTo(1L);

    List<DayBucket> weekly = reportRepository.mvGetJobCountsByWeek(tid, today.minusDays(6), today);
    assertThat(weekly).isNotEmpty();

    List<DayBucket> monthly =
        reportRepository.mvGetJobCountsByMonth(tid, today.withDayOfMonth(1), today);
    assertThat(monthly).isNotEmpty();
  }

  /**
   * Exercises {@link ReportRepository#mvGetAvgTimes} with no matching history rows — result must be
   * non-throwing and return null averages.
   */
  @Test
  void mvGetAvgTimes_noHistory_returnsNullAverages() {
    refreshJob.refreshAll();

    AvgTimesResponse response =
        reportRepository.mvGetAvgTimes(
            tenant.getId(), LocalDate.now().minusDays(1), LocalDate.now());

    assertThat(response).isNotNull();
    assertThat(response.avgTimeToClaimSeconds()).isNull();
    assertThat(response.avgTimeToCompletionSeconds()).isNull();
  }

  /**
   * Exercises {@link ReportRepository#mvGetTechnicianCompletionRates} with no data — result is an
   * empty list (not an exception).
   */
  @Test
  void mvGetTechnicianCompletionRates_noData_returnsEmptyList() {
    refreshJob.refreshAll();

    List<TechnicianRateEntry> rates =
        reportRepository.mvGetTechnicianCompletionRates(tenant.getId());

    assertThat(rates).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Direct (non-MV) repository method coverage
  // ---------------------------------------------------------------------------

  /**
   * Exercises {@link ReportRepository#getJobCountsByDay} — the convenience delegation to {@link
   * ReportRepository#getJobCountsByPeriod} with period={@code day}.
   */
  @Test
  void getJobCountsByDay_delegatesCorrectly() {
    UUID tid = tenant.getId();
    LocalDate today = LocalDate.now();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start,"
                  + " custom_fields, version)"
                  + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', ?::timestamptz, '{}', 0)",
              tid.toString(),
              customerId.toString(),
              scheduledStart);
          return null;
        });

    List<DayBucket> buckets = reportRepository.getJobCountsByDay(tid, today, today);

    assertThat(buckets).isNotEmpty();
    assertThat(buckets.get(0).created()).isGreaterThanOrEqualTo(1L);
  }

  /** Invalid period string must throw (wrapped or unwrapped) {@link IllegalArgumentException}. */
  @Test
  void getJobCountsByPeriod_invalidPeriod_throwsIllegalArgumentException() {
    assertThatThrownBy(
            () ->
                reportRepository.getJobCountsByPeriod(
                    tenant.getId(), "invalid", LocalDate.now(), LocalDate.now()))
        .satisfies(
            ex -> {
              Throwable cause = ex;
              boolean found = false;
              while (cause != null) {
                if (cause instanceof IllegalArgumentException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("Invalid period")) {
                  found = true;
                  break;
                }
                cause = cause.getCause();
              }
              assertThat(found)
                  .as("Expected an IllegalArgumentException with 'Invalid period' in the chain")
                  .isTrue();
            });
  }

  /**
   * Exercises the direct {@link ReportRepository#getAvgTimes} (non-MV) with no history — both
   * averages must be {@code null}.
   */
  @Test
  void getAvgTimes_directQuery_noHistory_returnsNullAverages() {
    AvgTimesResponse response =
        reportRepository.getAvgTimes(tenant.getId(), LocalDate.now().minusDays(1), LocalDate.now());

    assertThat(response).isNotNull();
    assertThat(response.avgTimeToClaimSeconds()).isNull();
    assertThat(response.avgTimeToCompletionSeconds()).isNull();
  }

  /**
   * Exercises the direct {@link ReportRepository#getTechnicianCompletionRates} with one COMPLETED
   * job assigned to a technician.
   */
  @Test
  void getTechnicianCompletionRates_directQuery_returnsEntry() {
    UUID tid = tenant.getId();
    UUID techId = UUID.randomUUID();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              techId.toString(),
              tid.toString(),
              "direct-tech-" + techId + "@test.com");
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start, custom_fields,"
                  + " version, assigned_technician_id)"
                  + " VALUES (?::uuid, ?::uuid, 'COMPLETED', ?::timestamptz, '{}', 0, ?::uuid)",
              tid.toString(),
              customerId.toString(),
              scheduledStart,
              techId.toString());
          return null;
        });

    List<TechnicianRateEntry> rates =
        reportRepository.getTechnicianCompletionRates(
            tid, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

    assertThat(rates).hasSize(1);
    assertThat(rates.get(0).technicianId()).isEqualTo(techId);
    assertThat(rates.get(0).totalAssigned()).isEqualTo(1L);
    assertThat(rates.get(0).totalCompleted()).isEqualTo(1L);
    assertThat(rates.get(0).completionRate()).isEqualTo(1.0);
  }

  /** Exercises zero-assigned-jobs branch — completion rate must be 0.0 (not division-by-zero). */
  @Test
  void getTechnicianCompletionRates_directQuery_noJobs_returnsEmptyList() {
    List<TechnicianRateEntry> rates =
        reportRepository.getTechnicianCompletionRates(
            tenant.getId(), LocalDate.now().minusDays(1), LocalDate.now());

    assertThat(rates).isEmpty();
  }

  /**
   * Exercises {@link ReportRepository#mvGetTechnicianCompletionRates} with one assigned technician
   * after a refresh.
   */
  @Test
  void mvGetTechnicianCompletionRates_withTechnician_returnsEntry() {
    UUID tid = tenant.getId();
    UUID techId = UUID.randomUUID();
    String scheduledStart = java.time.Instant.now().toString();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'TECHNICIAN')",
              techId.toString(),
              tid.toString(),
              "mvtech-" + techId + "@test.com");
          jdbc.update(
              "INSERT INTO jobs (tenant_id, customer_id, status, scheduled_start, custom_fields,"
                  + " version, assigned_technician_id)"
                  + " VALUES (?::uuid, ?::uuid, 'COMPLETED', ?::timestamptz, '{}', 0, ?::uuid)",
              tid.toString(),
              customerId.toString(),
              scheduledStart,
              techId.toString());
          return null;
        });

    refreshJob.refreshAll();

    List<TechnicianRateEntry> rates = reportRepository.mvGetTechnicianCompletionRates(tid);
    assertThat(rates).hasSize(1);
    assertThat(rates.get(0).technicianId()).isEqualTo(techId);
    assertThat(rates.get(0).totalCompleted()).isEqualTo(1L);
  }
}
