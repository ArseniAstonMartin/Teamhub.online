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
import com.teamhub.config.IntegrationTest;
import com.teamhub.eventing.payloads.JobClaimed;
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
 * Integration tests for TASK-058: {@link SilentPushConsumer} (PRD §15.2).
 *
 * <h3>Test steps covered</h3>
 *
 * <ol>
 *   <li>Tech A claims a job; Tech B's token receives a silent push with {@code body=null} and
 *       {@code data.type="SHARED_POOL_REFRESH"}. Tech A (claimer) is excluded.
 *   <li>Tech A does NOT receive the silent push.
 *   <li>Idempotency: delivering the same {@code JobClaimed} event twice triggers {@code sendBatch}
 *       only once (second delivery is a no-op via {@code processed_events}).
 * </ol>
 */
@SpringBootTest
@IntegrationTest
class SilentPushIT {

  @MockBean private ExpoPushClient expoPushClient;

  @Autowired
  @Qualifier("silentPushConsumer") private SilentPushConsumer silentPushConsumer;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserService userService;

  @Autowired private DeviceTokenService deviceTokenService;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private ObjectMapper objectMapper;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  private UUID tenantId;

  private static final String TOKEN_A = "ExponentPushToken[silent-tech-A]";

  private static final String TOKEN_B = "ExponentPushToken[silent-tech-B]";

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provisionWithAdminUser(
            "SilentPush Co " + UUID.randomUUID(), "silent-push+" + UUID.randomUUID() + "@test.com");
    tenantId = tenant.getId();

    when(expoPushClient.sendBatch(anyList())).thenReturn(Collections.emptyList());

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(() -> redis.opsForValue().get("relay:leader") != null);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM device_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
  }

  /**
   * Step 1 + Step 2: Tech A and Tech B both have TECHNICIAN tokens. Tech A claims the job.
   *
   * <p>Asserts that {@code sendBatch} is called with exactly 1 message — only Tech B's token. The
   * message must have {@code body=null} and {@code data.type="SHARED_POOL_REFRESH"}. Tech A (the
   * claimer) must NOT be present in the batch.
   */
  @Test
  @SuppressWarnings("unchecked")
  void step1And2_techBReceivesSilentPush_techAExcluded() throws Exception {
    User techA = registerTechnicianToken("techA@silent.com", TOKEN_A);
    registerTechnicianToken("techB@silent.com", TOKEN_B);

    UUID jobId = UUID.randomUUID();
    addJobClaimedToStream(UUID.randomUUID(), jobId, techA.getId());

    silentPushConsumer.pollOnce();

    ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(expoPushClient, times(1)).sendBatch(captor.capture());
    List<PushMessage> messages = captor.getValue();

    // Step 1: only Tech B's token in the batch
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).to()).isEqualTo(TOKEN_B);

    // Message is silent: no body, data carries type + jobId
    assertThat(messages.get(0).body()).isNull();
    assertThat(messages.get(0).data())
        .containsEntry("type", "SHARED_POOL_REFRESH")
        .containsEntry("jobId", jobId.toString());

    // Step 2: Tech A's token must not appear
    assertThat(messages.stream().map(PushMessage::to)).doesNotContain(TOKEN_A);
  }

  /**
   * Step 3: Idempotency — delivering the same {@code JobClaimed} event (same {@code event_id})
   * twice must result in {@code sendBatch} being called exactly once. The second delivery is
   * short-circuited by the {@code processed_events} de-dup gate in {@link
   * com.teamhub.eventing.AbstractOutboxConsumer}.
   */
  @Test
  void step3_idempotency_duplicateEventCallsSendBatchOnce() throws Exception {
    User techA = registerTechnicianToken("idempA@silent.com", TOKEN_A);
    registerTechnicianToken("idempB@silent.com", TOKEN_B);

    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    // Add the same logical event (same event_id) to the stream twice to simulate re-delivery.
    addJobClaimedToStream(eventId, jobId, techA.getId());
    addJobClaimedToStream(eventId, jobId, techA.getId());

    // Both records are read in a single poll cycle; the second is de-duped by processed_events.
    silentPushConsumer.pollOnce();

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

  /**
   * Adds a {@code JobClaimed} message directly to the {@code events:JobClaimed} Redis stream,
   * bypassing the transactional outbox for deterministic test control.
   */
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
}
