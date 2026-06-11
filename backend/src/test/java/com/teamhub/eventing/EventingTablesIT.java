package com.teamhub.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for TASK-029 eventing tables (PRD §22, §22.5).
 *
 * <p>Covers all three task test steps:
 *
 * <ol>
 *   <li>Step 1 — Schema: tables and indexes are present after V10 migration.
 *   <li>Step 2 — RLS: rows across two tenants are isolated; only the GUC-matching tenant's rows are
 *       visible when querying as {@code teamhub_app}.
 *   <li>Step 3 — Partitioning: {@code processed_events} has a child partition for the current month
 *       (2026-06) created by the migration.
 * </ol>
 *
 * <p>Tests connect as the {@code postgres} superuser (Testcontainers default, BYPASSRLS). RLS tests
 * use {@code SET LOCAL ROLE teamhub_app} inside an explicit {@link TransactionTemplate} so that the
 * LOCAL setting is transaction-scoped.
 */
@SpringBootTest
@IntegrationTest
class EventingTablesIT {

  @Autowired private InternalTenantService tenantService;

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Autowired private ProcessedEventRepository processedEventRepository;

  @Autowired private DelayedJobRepository delayedJobRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate txTemplate;

  private Tenant tenantA;

  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenantA =
        tenantService.provisionWithAdminUser(
            "Eventing Co A " + UUID.randomUUID(), "eventing-a+" + UUID.randomUUID() + "@test.com");
    tenantB =
        tenantService.provisionWithAdminUser(
            "Eventing Co B " + UUID.randomUUID(), "eventing-b+" + UUID.randomUUID() + "@test.com");
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM processed_events WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update(
        "DELETE FROM delayed_jobs WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update(
        "DELETE FROM outbox_events WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update(
        "DELETE FROM audit_log WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update(
        "DELETE FROM refresh_tokens WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id IN (?, ?)", tenantA.getId(), tenantB.getId());
  }

  // =========================================================================
  // Step 1 — Schema: tables and indexes present
  // =========================================================================

  @Test
  void step1_allThreeTablesExistAfterV10Migration() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
    assertThat(tables).contains("outbox_events", "delayed_jobs");

    // processed_events is a partitioned parent; it also appears in pg_tables.
    assertThat(tables).contains("processed_events");
  }

  @Test
  void step1_relayIndexExistsOnOutboxEvents() {
    // The relay-scan index is on (processed_at NULLS FIRST, created_at).
    boolean exists =
        Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                "SELECT EXISTS("
                    + "SELECT 1 FROM pg_indexes"
                    + " WHERE schemaname = 'public'"
                    + "   AND tablename = 'outbox_events'"
                    + "   AND indexname = 'idx_outbox_events_relay'"
                    + ")",
                Boolean.class));
    assertThat(exists).isTrue();
  }

  @Test
  void step1_statusRunAtIndexExistsOnDelayedJobs() {
    boolean exists =
        Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                "SELECT EXISTS("
                    + "SELECT 1 FROM pg_indexes"
                    + " WHERE schemaname = 'public'"
                    + "   AND tablename = 'delayed_jobs'"
                    + "   AND indexname = 'idx_delayed_jobs_status_run_at'"
                    + ")",
                Boolean.class));
    assertThat(exists).isTrue();
  }

  @Test
  void step1_rlsIsEnabledAndForcedOnAllThreeTables() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT relname, relrowsecurity, relforcerowsecurity"
                + " FROM pg_class"
                + " WHERE relname IN ('outbox_events', 'processed_events', 'delayed_jobs')"
                + "   AND relkind IN ('r', 'p')"
                + "   AND relnamespace = 'public'::regnamespace");

    // There must be exactly one row per table (partitioned parent counts as one relkind='p').
    assertThat(rows).hasSize(3);
    for (Map<String, Object> row : rows) {
      String tableName = (String) row.get("relname");
      assertThat((Boolean) row.get("relrowsecurity")).as("RLS enabled on %s", tableName).isTrue();
      assertThat((Boolean) row.get("relforcerowsecurity"))
          .as("FORCE RLS on %s", tableName)
          .isTrue();
    }
  }

  // =========================================================================
  // Step 2 — RLS isolation across two tenants
  // =========================================================================

  @Test
  void step2_outboxEventsRlsIsolatesAcrossTenants() {
    UUID aggIdA = UUID.randomUUID();
    UUID aggIdB = UUID.randomUUID();

    // Insert one event per tenant as postgres superuser (BYPASSRLS).
    OutboxEvent evA =
        outboxEventRepository.save(
            OutboxEvent.builder()
                .tenantId(tenantA.getId())
                .eventType("JobSubmitted")
                .payload("{\"job_id\":\"" + aggIdA + "\"}")
                .aggregateType("Job")
                .aggregateId(aggIdA)
                .build());

    outboxEventRepository.save(
        OutboxEvent.builder()
            .tenantId(tenantB.getId())
            .eventType("JobSubmitted")
            .payload("{\"job_id\":\"" + aggIdB + "\"}")
            .aggregateType("Job")
            .aggregateId(aggIdB)
            .build());

    // Switch to teamhub_app and assert only tenant A's row is visible with tenant A GUC.
    List<Map<String, Object>> rowsA =
        txTemplate.execute(
            status -> {
              jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
              jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantA.getId() + "'");
              return jdbcTemplate.queryForList("SELECT id FROM outbox_events");
            });

    assertThat(rowsA).hasSize(1);
    assertThat(rowsA.get(0).get("id").toString()).isEqualTo(evA.getId().toString());

    // Switch GUC to tenant B — only tenant B's row is visible.
    List<Map<String, Object>> rowsB =
        txTemplate.execute(
            status -> {
              jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
              jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantB.getId() + "'");
              return jdbcTemplate.queryForList("SELECT id FROM outbox_events");
            });

    assertThat(rowsB).hasSize(1);
    assertThat(rowsB.get(0).get("id").toString()).isNotEqualTo(evA.getId().toString());
  }

  @Test
  void step2_delayedJobsRlsIsolatesAcrossTenants() {
    Instant runAt = Instant.now().plusSeconds(300);

    DelayedJob jobA =
        delayedJobRepository.save(
            DelayedJob.builder()
                .tenantId(tenantA.getId())
                .kind("escalate_unclaimed_job")
                .payload("{\"job_id\":\"aaa\"}")
                .runAt(runAt)
                .build());

    delayedJobRepository.save(
        DelayedJob.builder()
            .tenantId(tenantB.getId())
            .kind("escalate_unclaimed_job")
            .payload("{\"job_id\":\"bbb\"}")
            .runAt(runAt)
            .build());

    // teamhub_app with tenant A GUC sees only jobA.
    List<Map<String, Object>> rowsA =
        txTemplate.execute(
            status -> {
              jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
              jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantA.getId() + "'");
              return jdbcTemplate.queryForList("SELECT id FROM delayed_jobs");
            });

    assertThat(rowsA).hasSize(1);
    assertThat(rowsA.get(0).get("id").toString()).isEqualTo(jobA.getId().toString());
  }

  @Test
  void step2_processedEventsRlsIsolatesAcrossTenants() {
    Instant now = Instant.now();
    UUID evIdA = UUID.randomUUID();
    UUID evIdB = UUID.randomUUID();

    processedEventRepository.save(new ProcessedEvent(evIdA, "audit-logger", now, tenantA.getId()));
    processedEventRepository.save(new ProcessedEvent(evIdB, "audit-logger", now, tenantB.getId()));

    // teamhub_app with tenant A GUC sees only tenant A's row.
    List<Map<String, Object>> rowsA =
        txTemplate.execute(
            status -> {
              jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
              jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantA.getId() + "'");
              return jdbcTemplate.queryForList("SELECT event_id FROM processed_events");
            });

    assertThat(rowsA).hasSize(1);
    assertThat(rowsA.get(0).get("event_id").toString()).isEqualTo(evIdA.toString());
  }

  @Test
  void step2_withoutGucSet_outboxEventsReturnsNoRows() {
    outboxEventRepository.save(
        OutboxEvent.builder()
            .tenantId(tenantA.getId())
            .eventType("JobSubmitted")
            .payload("{}")
            .aggregateType("Job")
            .aggregateId(UUID.randomUUID())
            .build());

    // teamhub_app with no GUC → deny-by-default → 0 rows.
    List<Map<String, Object>> rows =
        txTemplate.execute(
            status -> {
              jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");
              return jdbcTemplate.queryForList("SELECT id FROM outbox_events");
            });

    assertThat(rows).isEmpty();
  }

  // =========================================================================
  // Step 3 — processed_events partition for the current month
  // =========================================================================

  @Test
  void step3_processedEventsHasPartitionForCurrentMonth() {
    // The V10 migration creates processed_events_2026_06 for 2026-06.
    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    String expectedPartition =
        "processed_events_"
            + currentMonth.getYear()
            + "_"
            + String.format("%02d", currentMonth.getMonthValue());

    List<String> partitions =
        jdbcTemplate.queryForList(
            "SELECT relname FROM pg_class"
                + " WHERE relname LIKE 'processed_events_%'"
                + "   AND relkind = 'r'"
                + "   AND relnamespace = 'public'::regnamespace",
            String.class);

    assertThat(partitions).contains(expectedPartition);
  }

  @Test
  void step3_processedEventsIsDeclarativeRangePartitionedTable() {
    // partstrat = 'r' means RANGE partitioning.
    String partStrat =
        jdbcTemplate.queryForObject(
            "SELECT pt.partstrat"
                + " FROM pg_partitioned_table pt"
                + " JOIN pg_class c ON c.oid = pt.partrelid"
                + " WHERE c.relname = 'processed_events'"
                + "   AND c.relnamespace = 'public'::regnamespace",
            String.class);

    assertThat(partStrat).isEqualTo("r");
  }

  @Test
  void step3_insertIntoProcessedEventsLandsInCurrentMonthPartition() {
    Instant now = Instant.now();
    UUID evId = UUID.randomUUID();

    processedEventRepository.save(new ProcessedEvent(evId, "test-consumer", now, tenantA.getId()));

    // The row is physically in the current-month partition, not the parent.
    YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
    String partitionName =
        "processed_events_"
            + currentMonth.getYear()
            + "_"
            + String.format("%02d", currentMonth.getMonthValue());

    // Query directly from the partition table — must return the row.
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT event_id FROM " + partitionName + " WHERE event_id = ?", evId);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("event_id").toString()).isEqualTo(evId.toString());
  }

  // =========================================================================
  // Additional: entity builder guards
  // =========================================================================

  @Test
  void outboxEventBuilder_missingRequiredField_throwsIllegalState() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                OutboxEvent.builder()
                    .eventType("JobSubmitted")
                    .payload("{}")
                    .aggregateType("Job")
                    .aggregateId(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void delayedJobBuilder_missingRequiredField_throwsIllegalState() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                DelayedJob.builder()
                    .kind("escalate_unclaimed_job")
                    .payload("{}")
                    .runAt(Instant.now().plusSeconds(300))
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void delayedJobStatusTransition_setStatus_updatesField() {
    DelayedJob job =
        delayedJobRepository.save(
            DelayedJob.builder()
                .tenantId(tenantA.getId())
                .kind("escalate_unclaimed_job")
                .payload("{\"job_id\":\"test\"}")
                .runAt(Instant.now().plusSeconds(60))
                .build());

    job.setStatus(DelayedJobStatus.CANCELLED);
    delayedJobRepository.save(job);

    DelayedJob reloaded = delayedJobRepository.findById(job.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DelayedJobStatus.CANCELLED);
  }

  @Test
  void outboxEventRepository_findUnprocessed_returnsOnlyUnprocessedRows() {
    UUID aggId = UUID.randomUUID();
    outboxEventRepository.save(
        OutboxEvent.builder()
            .tenantId(tenantA.getId())
            .eventType("JobSubmitted")
            .payload("{}")
            .aggregateType("Job")
            .aggregateId(aggId)
            .build());

    List<OutboxEvent> unprocessed = outboxEventRepository.findUnprocessed();
    assertThat(unprocessed).isNotEmpty();
    assertThat(unprocessed).allSatisfy(e -> assertThat(e.getProcessedAt()).isNull());
  }

  @Test
  void delayedJobRepository_findDueJobs_returnsOnlyPendingJobsBeforeNow() {
    Instant past = Instant.now().minusSeconds(60);
    Instant future = Instant.now().plusSeconds(3600);

    DelayedJob dueJob =
        delayedJobRepository.save(
            DelayedJob.builder()
                .tenantId(tenantA.getId())
                .kind("escalate")
                .payload("{}")
                .runAt(past)
                .build());

    delayedJobRepository.save(
        DelayedJob.builder()
            .tenantId(tenantA.getId())
            .kind("escalate")
            .payload("{}")
            .runAt(future)
            .build());

    List<DelayedJob> due = delayedJobRepository.findDueJobs(Instant.now());
    assertThat(due).extracting(DelayedJob::getId).contains(dueJob.getId());
    assertThat(due)
        .allSatisfy(j -> assertThat(j.getRunAt()).isBefore(Instant.now().plusSeconds(1)));
  }
}
