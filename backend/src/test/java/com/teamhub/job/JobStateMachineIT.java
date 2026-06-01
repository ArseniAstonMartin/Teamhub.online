package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.FieldDefinition;
import com.teamhub.schemas.SchemaService;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link JobStateMachineService} (PRD §9.2, TASK-043).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_submittedToAssigned_byTechnician_succeedsAndWritesHistory()}):
 *       {@code SUBMITTED → ASSIGNED_TO_TECHNICIAN} by {@code TECHNICIAN} — succeeds; history row
 *       and outbox row exist.
 *   <li><b>Step 2</b> ({@link #step2_submittedToCompleted_throwsIllegalTransition()}): {@code
 *       SUBMITTED → COMPLETED} by any role — throws {@link IllegalStateTransitionException} (HTTP
 *       409, {@code INVALID_TRANSITION}).
 *   <li><b>Step 3</b> ({@link #step3_completedToAny_byDispatcher_throwsIllegalTransition()}):
 *       {@code COMPLETED → anything} by {@code DISPATCHER} — throws {@link
 *       IllegalStateTransitionException}.
 *   <li><b>Step 4</b> ({@link #step4_completedToSubmitted_byAdmin_succeeds()}): {@code COMPLETED →
 *       SUBMITTED} by {@code ADMIN} — succeeds (PRD §9.2: "Completed → (any) | Admin only").
 *   <li><b>Step 5</b> ({@link #step5_historyRowExistsForEverySuccessfulTransition()}): {@code
 *       job_status_history} rows exist for every successful transition in steps 1 and 4.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobStateMachineIT {

  private static final String REQUIRED_FIELD_KEY = "vehicle_make";

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private SchemaService schemaService;

  @Autowired private JobRepository jobRepository;

  @Autowired private JobStatusHistoryRepository historyRepository;

  @Autowired private JobStateMachineService stateMachine;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID customerId;

  private UUID schemaId;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "StateMachine Co " + UUID.randomUUID(),
                    "state-machine+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();

    customerId =
        tx.execute(
            s -> {
              Customer c = buildCustomer(tid);
              customerRepository.save(c);
              return c.getId();
            });

    schemaId =
        tx.execute(
            s -> {
              FieldDefinition fd =
                  new FieldDefinition(
                      REQUIRED_FIELD_KEY, "Vehicle Make", "text", true, 1, null, null, null, null);
              AppointmentSchema schema =
                  schemaService.publishNewVersion(tid, "State Machine Schema", List.of(fd));
              return schema.getId();
            });
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM appointment_schemas WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  // =========================================================================
  // Step 1 — SUBMITTED → ASSIGNED_TO_TECHNICIAN by TECHNICIAN: succeeds
  // =========================================================================

  /**
   * TASK-043 Step 1: {@code SUBMITTED → ASSIGNED_TO_TECHNICIAN} by {@code TECHNICIAN} must succeed,
   * persist the updated job status, write a {@code job_status_history} row, and publish a {@code
   * JobStatusChanged} outbox event.
   */
  @Test
  void step1_submittedToAssigned_byTechnician_succeedsAndWritesHistory() {
    UUID jobId = createJob(JobStatus.SUBMITTED);
    UUID actorId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    stateMachine.transition(
        jobId, JobStatus.ASSIGNED_TO_TECHNICIAN, actorId, "TECHNICIAN", "Technician claiming job");
    TenantContext.clear();

    // Job status updated
    Job updated = jobRepository.findById(jobId).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(JobStatus.ASSIGNED_TO_TECHNICIAN);

    // History row written
    List<JobStatusHistory> history = historyRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    assertThat(history).hasSize(1);
    JobStatusHistory row = history.get(0);
    assertThat(row.getFromStatus()).isEqualTo("SUBMITTED");
    assertThat(row.getToStatus()).isEqualTo("ASSIGNED_TO_TECHNICIAN");
    assertThat(row.getActorRole()).isEqualTo("TECHNICIAN");
    assertThat(row.getActorUserId()).isEqualTo(actorId);

    // Outbox event published
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
  // Step 2 — SUBMITTED → COMPLETED: 409 INVALID_TRANSITION
  // =========================================================================

  /**
   * TASK-043 Step 2: {@code SUBMITTED → COMPLETED} is not a valid transition regardless of role;
   * must throw {@link IllegalStateTransitionException} (HTTP 409, code {@code INVALID_TRANSITION}).
   */
  @Test
  void step2_submittedToCompleted_throwsIllegalTransition() {
    UUID jobId = createJob(JobStatus.SUBMITTED);
    UUID actorId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    try {
      assertThatThrownBy(
              () ->
                  stateMachine.transition(jobId, JobStatus.COMPLETED, actorId, "DISPATCHER", null))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasFieldOrPropertyWithValue("httpStatus", org.springframework.http.HttpStatus.CONFLICT)
          .satisfies(
              ex ->
                  assertThat(((IllegalStateTransitionException) ex).getCode())
                      .isEqualTo(IllegalStateTransitionException.CODE));
    } finally {
      TenantContext.clear();
    }
  }

  // =========================================================================
  // Step 3 — COMPLETED → anything by DISPATCHER: 409 INVALID_TRANSITION
  // =========================================================================

  /**
   * TASK-043 Step 3: {@code COMPLETED → anything} by {@code DISPATCHER} is not allowed (PRD §9.2:
   * only Admin may override a completed job); must throw {@link IllegalStateTransitionException}.
   */
  @Test
  void step3_completedToAny_byDispatcher_throwsIllegalTransition() {
    UUID jobId = createJob(JobStatus.COMPLETED);
    UUID actorId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    try {
      assertThatThrownBy(
              () ->
                  stateMachine.transition(jobId, JobStatus.SUBMITTED, actorId, "DISPATCHER", null))
          .isInstanceOf(IllegalStateTransitionException.class)
          .hasFieldOrPropertyWithValue("httpStatus", org.springframework.http.HttpStatus.CONFLICT)
          .satisfies(
              ex ->
                  assertThat(((IllegalStateTransitionException) ex).getCode())
                      .isEqualTo("INVALID_TRANSITION"));
    } finally {
      TenantContext.clear();
    }
  }

  // =========================================================================
  // Step 4 — COMPLETED → SUBMITTED by ADMIN: succeeds (PRD §9.2)
  // =========================================================================

  /**
   * TASK-043 Step 4: PRD §9.2 states "Completed → (any) | Admin only". Therefore {@code COMPLETED →
   * SUBMITTED} by {@code ADMIN} must succeed.
   *
   * <p>Decision documented: DISPATCHER is explicitly excluded from this transition; only ADMIN can
   * re-open a completed job (confirmed by PRD §9.2 "Admin only").
   */
  @Test
  void step4_completedToSubmitted_byAdmin_succeeds() {
    UUID jobId = createJob(JobStatus.COMPLETED);
    UUID actorId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    stateMachine.transition(jobId, JobStatus.SUBMITTED, actorId, "ADMIN", "Admin re-open");
    TenantContext.clear();

    Job updated = jobRepository.findById(jobId).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(JobStatus.SUBMITTED);
  }

  // =========================================================================
  // Step 5 — job_status_history rows exist for every successful transition
  // =========================================================================

  /**
   * TASK-043 Step 5: every successful transition must produce a {@code job_status_history} row.
   * This test executes the same two successful transitions as steps 1 and 4 in sequence on a single
   * job and verifies that both rows are present.
   */
  @Test
  void step5_historyRowExistsForEverySuccessfulTransition() {
    UUID jobId = createJob(JobStatus.SUBMITTED);
    UUID techId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    TenantContext.set(tenant.getId());

    // Transition 1: SUBMITTED → ASSIGNED_TO_TECHNICIAN by TECHNICIAN
    stateMachine.transition(jobId, JobStatus.ASSIGNED_TO_TECHNICIAN, techId, "TECHNICIAN", "claim");

    // Advance to COMPLETED via intermediate states to test Admin re-open
    stateMachine.transition(jobId, JobStatus.IN_PROGRESS, techId, "TECHNICIAN", "start");
    stateMachine.transition(jobId, JobStatus.DONE_PENDING_APPROVAL, techId, "TECHNICIAN", "done");
    stateMachine.transition(jobId, JobStatus.COMPLETED, adminId, "ADMIN", "approve");

    // Transition 5: COMPLETED → SUBMITTED by ADMIN (re-open)
    stateMachine.transition(jobId, JobStatus.SUBMITTED, adminId, "ADMIN", "re-open");

    TenantContext.clear();

    List<JobStatusHistory> history = historyRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    assertThat(history).hasSize(5);

    assertThat(history.get(0).getFromStatus()).isEqualTo("SUBMITTED");
    assertThat(history.get(0).getToStatus()).isEqualTo("ASSIGNED_TO_TECHNICIAN");

    assertThat(history.get(4).getFromStatus()).isEqualTo("COMPLETED");
    assertThat(history.get(4).getToStatus()).isEqualTo("SUBMITTED");
  }

  // =========================================================================
  // Additional: job not found → 404
  // =========================================================================

  /**
   * Attempting to transition a non-existent job must throw {@link JobNotFoundException} (HTTP 404,
   * code {@code JOB_NOT_FOUND}).
   */
  @Test
  void jobNotFound_throwsJobNotFoundException() {
    UUID missingJobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    TenantContext.set(tenant.getId());
    try {
      assertThatThrownBy(
              () ->
                  stateMachine.transition(
                      missingJobId, JobStatus.ASSIGNED_TO_TECHNICIAN, actorId, "TECHNICIAN", null))
          .isInstanceOf(JobNotFoundException.class)
          .hasFieldOrPropertyWithValue("httpStatus", org.springframework.http.HttpStatus.NOT_FOUND)
          .satisfies(
              ex -> assertThat(((JobNotFoundException) ex).getCode()).isEqualTo("JOB_NOT_FOUND"));
    } finally {
      TenantContext.clear();
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Creates a job with the given initial status in a committed transaction. Returns the job's UUID.
   */
  private UUID createJob(JobStatus initialStatus) {
    return tx.execute(
        s -> {
          Job j = new Job();
          j.setTenantId(tenant.getId());
          j.setCustomerId(customerId);
          j.setAppointmentSchemaId(schemaId);
          j.setStatus(initialStatus);
          j.setCustomFields(Map.of(REQUIRED_FIELD_KEY, "Toyota"));
          jobRepository.save(j);
          return j.getId();
        });
  }

  private Customer buildCustomer(UUID tenantId) {
    Customer c = new Customer();
    c.setTenantId(tenantId);
    c.setName("State Machine Customer");
    c.setPhone("+15033334444");
    c.setAddressLine("200 Test Blvd");
    c.setCity("Portland");
    c.setState("OR");
    c.setZip("97201");
    org.locationtech.jts.geom.GeometryFactory gf =
        new org.locationtech.jts.geom.GeometryFactory(
            new org.locationtech.jts.geom.PrecisionModel(), 4326);
    c.setLocation(gf.createPoint(new org.locationtech.jts.geom.Coordinate(-122.6784, 45.5231)));
    return c;
  }
}
