package com.teamhub.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.UserService;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.eventing.DelayedJobDispatcher;
import com.teamhub.eventing.payloads.JobClaimed;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStatus;
import com.teamhub.push.DevicePlatform;
import com.teamhub.push.DeviceTokenService;
import com.teamhub.push.ExpoPushClient;
import com.teamhub.push.PushMessage;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for TASK-046: job escalation flow (PRD §10.3).
 *
 * <h3>Test steps covered</h3>
 *
 * <ol>
 *   <li>AC1 / Step 1: {@code JobSubmitted} → {@code EscalationSchedulerConsumer} creates a {@code
 *       delayed_jobs} row with {@code kind='escalate_unclaimed_job'} and schedules it in Redis.
 *   <li>AC2 / Step 2+3: {@code JobClaimed} → {@code EscalationCancelerConsumer} sets the matching
 *       row to {@code status='CANCELLED'}.
 *   <li>AC3: Handler fires, job still SUBMITTED → all Admin tokens receive {@code "JOB NOT
 *       HANDLED"} push.
 *   <li>AC3 (negative): Handler fires, job already claimed → no push sent.
 * </ol>
 */
@SpringBootTest
@IntegrationTest
class EscalationIT {

  @MockBean private ExpoPushClient expoPushClient;

  @Autowired
  @Qualifier("escalationSchedulerConsumer") private EscalationSchedulerConsumer escalationSchedulerConsumer;

  @Autowired
  @Qualifier("escalationCancelerConsumer") private EscalationCancelerConsumer escalationCancelerConsumer;

  @Autowired private DelayedJobDispatcher dispatcher;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserService userService;

  @Autowired private DeviceTokenService deviceTokenService;

  @Autowired private JobRepository jobRepository;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private ObjectMapper objectMapper;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  private UUID tenantId;

  private UUID customerId;

  private static final String ADMIN_TOKEN = "ExponentPushToken[esc-admin-001]";

  private static final String ADMIN_TOKEN_2 = "ExponentPushToken[esc-admin-002]";

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provisionWithAdminUser(
            "Escalation Co " + UUID.randomUUID(), "esc+" + UUID.randomUUID() + "@test.com");
    tenantId = tenant.getId();

    customerId =
        txTemplate.execute(
            s -> {
              Customer c = buildCustomer(tenantId);
              customerRepository.save(c);
              return c.getId();
            });

    when(expoPushClient.sendBatch(anyList())).thenReturn(Collections.emptyList());

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(() -> redis.opsForValue().get("relay:leader") != null);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    redis.delete("delayed_jobs");
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM device_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM in_app_alerts WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM job_status_history WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
    // Delete processed_events last — background consumers may insert rows during the cleanup
    // above; deleting immediately before the tenant ensures FK consistency.
    jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
  }

  // =========================================================================
  // AC1 — JobSubmitted → delayed_jobs row created with correct kind
  // =========================================================================

  /**
   * Step 1 (AC1): Publish a {@code JobSubmitted} event; assert the consumer creates a {@code
   * delayed_jobs} row with {@code kind='escalate_unclaimed_job'}, {@code status='PENDING'}, and the
   * job's ID in the payload.
   */
  @Test
  void step1_jobSubmitted_delayedJobScheduled() throws Exception {
    UUID jobId = UUID.randomUUID();
    addJobSubmittedToStream(UUID.randomUUID(), jobId);

    escalationSchedulerConsumer.pollOnce();

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT status, kind, payload FROM delayed_jobs" + " WHERE tenant_id = ? AND kind = ?",
            tenantId,
            EscalationSchedulerConsumer.JOB_KIND);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("status")).isEqualTo("PENDING");
    assertThat(rows.get(0).get("kind")).isEqualTo("escalate_unclaimed_job");

    String payload = String.valueOf(rows.get(0).get("payload"));
    assertThat(payload).contains(jobId.toString());

    // The entry must also appear in the Redis sorted set
    assertThat(redis.opsForZSet().size("delayed_jobs"))
        .as("Redis sorted set must contain the scheduled job entry")
        .isGreaterThanOrEqualTo(1L);
  }

  // =========================================================================
  // AC2 + Step 2+3 — JobClaimed → matching delayed_jobs row CANCELLED
  // =========================================================================

  /**
   * Step 2 + Step 3 (AC2): Schedule an escalation job, then publish a {@code JobClaimed} event;
   * assert the {@code EscalationCancelerConsumer} sets the row to {@code status='CANCELLED'}.
   */
  @Test
  void step2And3_jobClaimed_delayedJobCancelled() throws Exception {
    UUID jobId = UUID.randomUUID();

    // Schedule the escalation row as if JobSubmitted already fired.
    TenantContext.set(tenantId);
    String payload = objectMapper.writeValueAsString(new EscalationPayload(jobId));
    dispatcher.schedule(
        tenantId,
        EscalationSchedulerConsumer.JOB_KIND,
        payload,
        Instant.now().plus(EscalationSchedulerConsumer.ESCALATION_DELAY));
    TenantContext.clear();

    // Publish JobClaimed
    UUID technicianId = UUID.randomUUID();
    addJobClaimedToStream(UUID.randomUUID(), jobId, technicianId);

    escalationCancelerConsumer.pollOnce();

    String status =
        jdbc.queryForObject(
            "SELECT status FROM delayed_jobs WHERE tenant_id = ? AND kind = ? AND payload ->> 'jobId' = ?",
            String.class,
            tenantId,
            EscalationSchedulerConsumer.JOB_KIND,
            jobId.toString());

    assertThat(status)
        .as("delayed_jobs row must be CANCELLED after JobClaimed")
        .isEqualTo("CANCELLED");
  }

  // =========================================================================
  // AC3 — Handler fires: job still SUBMITTED → admin push sent
  // =========================================================================

  /**
   * AC3 (positive): The handler fires for a job still in {@code SUBMITTED} status; all active Admin
   * device tokens for the tenant receive a push with {@code "JOB NOT HANDLED"}.
   */
  @Test
  @SuppressWarnings("unchecked")
  void ac3_handlerFires_jobStillSubmitted_adminsPushReceived() throws Exception {
    User admin1 = registerAdminToken("admin1@esc.com", ADMIN_TOKEN);
    User admin2 = registerAdminToken("admin2@esc.com", ADMIN_TOKEN_2);

    UUID jobId = createJobWithStatus(JobStatus.SUBMITTED);

    scheduleEscalationImmediately(jobId);

    // Wait for the background @Scheduled dispatcher tick to process the due job.
    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> verify(expoPushClient, times(1)).sendBatch(anyList()));

    ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(expoPushClient, times(1)).sendBatch(captor.capture());

    List<PushMessage> messages = captor.getValue();
    assertThat(messages).hasSize(2);
    assertThat(messages)
        .allSatisfy(
            msg -> {
              assertThat(msg.body()).isEqualTo(EscalateUnclaimedJobHandler.PUSH_BODY);
              assertThat(msg.title()).isEqualTo(EscalateUnclaimedJobHandler.PUSH_TITLE);
              assertThat(msg.data()).containsEntry("jobId", jobId.toString());
              assertThat(msg.data()).containsEntry("screen", "JobDetail");
            });
    assertThat(messages.stream().map(PushMessage::to))
        .containsExactlyInAnyOrder(ADMIN_TOKEN, ADMIN_TOKEN_2);

    // Verify the admin users are actually referenced (suppress unused warning)
    assertThat(admin1).isNotNull();
    assertThat(admin2).isNotNull();
  }

  // =========================================================================
  // AC3 (negative) — Handler fires: job already claimed → no push
  // =========================================================================

  /**
   * AC3 (negative): The handler fires but the job has already been claimed ({@code
   * ASSIGNED_TO_TECHNICIAN}); no push must be sent.
   */
  @Test
  void ac3Negative_handlerFires_jobAlreadyClaimed_noPushSent() throws Exception {
    registerAdminToken("admin3@esc.com", ADMIN_TOKEN);

    UUID jobId = createJobWithStatus(JobStatus.ASSIGNED_TO_TECHNICIAN);

    scheduleEscalationImmediately(jobId);

    // Wait for the dispatcher to process the due job (becomes DONE without sending a push).
    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              List<Map<String, Object>> rows =
                  jdbc.queryForList(
                      "SELECT status FROM delayed_jobs WHERE tenant_id = ? AND kind = ?",
                      tenantId,
                      EscalationSchedulerConsumer.JOB_KIND);
              return !rows.isEmpty() && "DONE".equals(rows.get(0).get("status"));
            });

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  // =========================================================================
  // Idempotency — same JobSubmitted event twice → one delayed_jobs row
  // =========================================================================

  /**
   * Idempotency: delivering the same {@code JobSubmitted} event (same {@code event_id}) twice must
   * result in exactly one {@code delayed_jobs} row (the second delivery is short-circuited by the
   * {@code processed_events} de-dup gate).
   */
  @Test
  void idempotency_duplicateJobSubmittedEvent_singleDelayedJobRow() throws Exception {
    UUID jobId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    addJobSubmittedToStream(eventId, jobId);
    addJobSubmittedToStream(eventId, jobId);

    escalationSchedulerConsumer.pollOnce();

    long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM delayed_jobs WHERE tenant_id = ? AND kind = ? AND payload ->> 'jobId' = ?",
            Long.class,
            tenantId,
            EscalationSchedulerConsumer.JOB_KIND,
            jobId.toString());

    assertThat(count)
        .as("exactly one delayed_jobs row must exist for a duplicate event_id")
        .isEqualTo(1L);
  }

  // =========================================================================
  // No admin tokens — handler skips push gracefully
  // =========================================================================

  /**
   * When there are no active Admin tokens for the tenant the handler exits without calling {@code
   * sendBatch}.
   */
  @Test
  void noAdminTokens_handlerSkipsPush() throws Exception {
    UUID jobId = createJobWithStatus(JobStatus.SUBMITTED);

    scheduleEscalationImmediately(jobId);

    // Wait for the dispatcher to process the due job (becomes DONE; sendBatch must not be called).
    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              List<Map<String, Object>> rows =
                  jdbc.queryForList(
                      "SELECT status FROM delayed_jobs WHERE tenant_id = ? AND kind = ?",
                      tenantId,
                      EscalationSchedulerConsumer.JOB_KIND);
              return !rows.isEmpty() && "DONE".equals(rows.get(0).get("status"));
            });

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private User registerAdminToken(String email, String expoToken) {
    User user = userService.createUser(tenantId, email, "Pass1234!", UserRole.ADMIN, "Admin");
    deviceTokenService.register(user.getId(), tenantId, expoToken, DevicePlatform.ANDROID);
    return user;
  }

  private UUID createJobWithStatus(JobStatus status) {
    return txTemplate.execute(
        s -> {
          Job job = new Job();
          job.setTenantId(tenantId);
          job.setCustomerId(customerId);
          job.setStatus(status);
          job.setCustomFields(Map.of());
          jobRepository.save(job);
          return job.getId();
        });
  }

  private void scheduleEscalationImmediately(UUID jobId) throws Exception {
    TenantContext.set(tenantId);
    String payload = objectMapper.writeValueAsString(new EscalationPayload(jobId));
    dispatcher.schedule(
        tenantId, EscalationSchedulerConsumer.JOB_KIND, payload, Instant.now().minusSeconds(1));
    TenantContext.clear();
  }

  private void addJobSubmittedToStream(UUID eventId, UUID jobId) throws Exception {
    String payloadJson =
        objectMapper.writeValueAsString(
            new JobSubmitted(eventId, tenantId, jobId, customerId, "PLUMBING", null));
    redis
        .opsForStream()
        .add(
            "events:JobSubmitted",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenantId.toString(),
                "payload", payloadJson));
  }

  private void addJobClaimedToStream(UUID eventId, UUID jobId, UUID technicianId) throws Exception {
    String payloadJson =
        objectMapper.writeValueAsString(
            new JobClaimed(eventId, tenantId, jobId, technicianId, Instant.now()));
    redis
        .opsForStream()
        .add(
            "events:JobClaimed",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenantId.toString(),
                "payload", payloadJson));
  }

  private Customer buildCustomer(UUID tid) {
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    Customer c = new Customer();
    c.setTenantId(tid);
    c.setName("Escalation Test Customer");
    c.setPhone("+15551234567");
    c.setAddressLine("100 Test St");
    c.setCity("Austin");
    c.setState("TX");
    c.setZip("78701");
    c.setLocation(gf.createPoint(new Coordinate(-97.7431, 30.2672)));
    return c;
  }
}
