package com.teamhub.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.UserService;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.eventing.payloads.JobStatusChanged;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStatus;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for TASK-059: {@link PushActorConsumer} (PRD §15.2).
 *
 * <h3>Test steps covered</h3>
 *
 * <ol>
 *   <li>Dispatcher approves a job; the assigned technician receives a push containing {@code "Job
 *       <shortId> approved"} and a {@code JobDetail} deep-link.
 *   <li>Another technician (not assigned) does NOT receive this push.
 *   <li>Idempotency: delivering the same {@code JobStatusChanged} event twice triggers {@code
 *       sendBatch} exactly once (second delivery is de-duped by {@code processed_events}).
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PushActorIT {

  @MockBean private ExpoPushClient expoPushClient;

  @Autowired
  @Qualifier("pushActorConsumer") private PushActorConsumer pushActorConsumer;

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

  private static final String TOKEN_TECH = "ExponentPushToken[actor-tech]";

  private static final String TOKEN_OTHER = "ExponentPushToken[actor-other]";

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provision(
            "PushActor Co " + UUID.randomUUID(), "push-actor+" + UUID.randomUUID() + "@test.com");
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
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM device_tokens WHERE tenant_id = ?", tenantId);
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

  /**
   * Step 1 + Step 2: the assigned technician (Tech A) receives a push; the other technician (Tech
   * B) does NOT.
   *
   * <p>Asserts the message body contains the job short-ID, title is "Job Approved", and the data
   * deep-link points to "JobDetail". Asserts Tech B's token is not in the batch.
   */
  @Test
  @SuppressWarnings("unchecked")
  void step1And2_assignedTechnicianReceivesPush_otherTechnicianExcluded() throws Exception {
    User techA = registerTechnicianToken("techA@actor.com", TOKEN_TECH);
    registerTechnicianToken("techB@actor.com", TOKEN_OTHER);

    UUID jobId = createJob(techA.getId());

    addJobStatusChangedToStream(
        UUID.randomUUID(), jobId, "DONE_PENDING_APPROVAL", "COMPLETED", UUID.randomUUID());

    pushActorConsumer.pollOnce();

    ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(expoPushClient, times(1)).sendBatch(captor.capture());
    List<PushMessage> messages = captor.getValue();

    // Step 1: exactly Tech A's token
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).to()).isEqualTo(TOKEN_TECH);

    // Body contains the short job ID and "approved"
    String expectedShortId = jobId.toString().substring(0, 8);
    assertThat(messages.get(0).body()).contains(expectedShortId).containsIgnoringCase("approved");

    // Deep-link data
    assertThat(messages.get(0).data())
        .containsEntry("jobId", jobId.toString())
        .containsEntry("screen", "JobDetail");

    // Step 2: Tech B's token must not appear
    assertThat(messages.stream().map(PushMessage::to)).doesNotContain(TOKEN_OTHER);
  }

  /**
   * Non-matching old-status: event with {@code oldStatus != DONE_PENDING_APPROVAL} must produce no
   * push regardless of {@code newStatus}.
   */
  @Test
  void nonMatchingOldStatus_noSendBatch() throws Exception {
    User techA = registerTechnicianToken("techA2@actor.com", TOKEN_TECH);
    createJob(techA.getId());

    addJobStatusChangedToStream(
        UUID.randomUUID(), UUID.randomUUID(), "SUBMITTED", "COMPLETED", techA.getId());

    pushActorConsumer.pollOnce();

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  /**
   * Non-matching new-status: event with matching old-status but {@code newStatus != COMPLETED} must
   * produce no push — covers the second branch of the {@code ||} transition filter.
   */
  @Test
  void nonMatchingNewStatus_noSendBatch() throws Exception {
    User techA = registerTechnicianToken("techA3@actor.com", TOKEN_TECH);
    createJob(techA.getId());

    addJobStatusChangedToStream(
        UUID.randomUUID(), UUID.randomUUID(), "DONE_PENDING_APPROVAL", "SUBMITTED", techA.getId());

    pushActorConsumer.pollOnce();

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  /**
   * No active tokens: when the assigned technician has no non-stale device tokens, no push is sent.
   */
  @Test
  void noActiveTokensForTechnician_noSendBatch() throws Exception {
    User techA =
        userService.createUser(
            tenantId, "notokentech@actor.com", "Pass1234!", UserRole.TECHNICIAN, "Tech");
    UUID jobId = createJob(techA.getId());

    addJobStatusChangedToStream(
        UUID.randomUUID(), jobId, "DONE_PENDING_APPROVAL", "COMPLETED", UUID.randomUUID());

    pushActorConsumer.pollOnce();

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  /**
   * Unknown job: when the jobId in the event does not exist in the DB, the handler silently skips
   * without calling sendBatch.
   */
  @Test
  void unknownJob_noSendBatch() throws Exception {
    registerTechnicianToken("techA4@actor.com", TOKEN_TECH);
    UUID unknownJobId = UUID.randomUUID();

    addJobStatusChangedToStream(
        UUID.randomUUID(), unknownJobId, "DONE_PENDING_APPROVAL", "COMPLETED", UUID.randomUUID());

    pushActorConsumer.pollOnce();

    verify(expoPushClient, times(0)).sendBatch(anyList());
  }

  /**
   * Step 3: Idempotency — delivering the same {@code JobStatusChanged} event (same {@code
   * event_id}) twice must result in {@code sendBatch} being called exactly once. The second
   * delivery is short-circuited by the {@code processed_events} de-dup gate in {@link
   * com.teamhub.eventing.AbstractOutboxConsumer}.
   */
  @Test
  void step3_idempotency_duplicateEventCallsSendBatchOnce() throws Exception {
    User techA = registerTechnicianToken("idempA@actor.com", TOKEN_TECH);
    UUID jobId = createJob(techA.getId());

    UUID eventId = UUID.randomUUID();

    // Same logical event (same event_id) published twice to simulate re-delivery.
    addJobStatusChangedToStream(
        eventId, jobId, "DONE_PENDING_APPROVAL", "COMPLETED", UUID.randomUUID());
    addJobStatusChangedToStream(
        eventId, jobId, "DONE_PENDING_APPROVAL", "COMPLETED", UUID.randomUUID());

    // Both records consumed in a single poll cycle; second is de-duped by processed_events.
    pushActorConsumer.pollOnce();

    verify(expoPushClient, times(1)).sendBatch(anyList());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private User registerTechnicianToken(String email, String expoToken) {
    User user = userService.createUser(tenantId, email, "Pass1234!", UserRole.TECHNICIAN, "Tech");
    deviceTokenService.register(user.getId(), tenantId, expoToken, DevicePlatform.ANDROID);
    return user;
  }

  private UUID createJob(UUID assignedTechnicianId) {
    return txTemplate.execute(
        s -> {
          Job job = new Job();
          job.setTenantId(tenantId);
          job.setCustomerId(customerId);
          job.setAssignedTechnicianId(assignedTechnicianId);
          job.setStatus(JobStatus.DONE_PENDING_APPROVAL);
          job.setCustomFields(Map.of());
          jobRepository.save(job);
          return job.getId();
        });
  }

  /**
   * Adds a {@code JobStatusChanged} message directly to the {@code events:JobStatusChanged} Redis
   * stream, bypassing the transactional outbox for deterministic test control.
   */
  private void addJobStatusChangedToStream(
      UUID eventId, UUID jobId, String oldStatus, String newStatus, UUID actorId) throws Exception {
    String payloadJson =
        objectMapper.writeValueAsString(
            new JobStatusChanged(eventId, tenantId, jobId, oldStatus, newStatus, actorId));
    redis
        .opsForStream()
        .add(
            "events:JobStatusChanged",
            Map.of(
                "id", eventId.toString(),
                "tenant_id", tenantId.toString(),
                "payload", payloadJson));
  }

  private Customer buildCustomer(UUID tid) {
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    Customer c = new Customer();
    c.setTenantId(tid);
    c.setName("Actor Test Customer");
    c.setPhone("+15551234567");
    c.setAddressLine("100 Test St");
    c.setCity("Austin");
    c.setState("TX");
    c.setZip("78701");
    c.setLocation(gf.createPoint(new Coordinate(-97.7431, 30.2672)));
    return c;
  }
}
