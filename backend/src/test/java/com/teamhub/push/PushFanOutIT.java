package com.teamhub.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.UserService;
import com.teamhub.config.IntegrationTest;
import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.JobSubmitted;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for TASK-057: {@link PushFanOutConsumer} and {@link ExpoPushClient} (PRD
 * §15.2).
 *
 * <h3>Test steps covered</h3>
 *
 * <ol>
 *   <li>3 active TECHNICIAN tokens → {@code sendBatch} called with 3 messages with correct body and
 *       deep-link data.
 *   <li>1 token marked stale → {@code sendBatch} called with only 2 messages.
 *   <li>{@link ExpoPushClient} with simulated {@code DeviceNotRegistered} ticket → {@code
 *       deviceTokenService.markStale()} invoked for the offending token.
 *   <li>{@link ExpoPushClient} batching: 101 messages split into 2 HTTP requests.
 * </ol>
 */
@SpringBootTest
@IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushFanOutIT {

  @MockBean private ExpoPushClient expoPushClient;

  @SpyBean private DeviceTokenService deviceTokenService;

  @Autowired private OutboxPublisher publisher;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserService userService;

  @Autowired private DeviceTokenRepository deviceTokenRepository;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private ObjectMapper objectMapper;

  private TransactionTemplate txTemplate;

  private Tenant tenant;

  private UUID tenantId;

  private static final String TOKEN_1 = "ExponentPushToken[tech-device-001]";

  private static final String TOKEN_2 = "ExponentPushToken[tech-device-002]";

  private static final String TOKEN_3 = "ExponentPushToken[tech-device-003]";

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(txManager);
    tenant =
        tenantService.provisionWithAdminUser(
            "PushFanOut Co " + UUID.randomUUID(),
            "push-fan-out+" + UUID.randomUUID() + "@test.com");
    tenantId = tenant.getId();

    when(expoPushClient.sendBatch(anyList())).thenReturn(Collections.emptyList());

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(() -> redis.opsForValue().get("relay:leader") != null);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM device_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tenantId);
    // Delete processed_events last — background consumers may insert rows during the cleanup
    // above; deleting immediately before the tenant ensures FK consistency.
    jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
  }

  /**
   * Step 1: Register 3 TECHNICIAN device tokens. Publish a {@code JobSubmitted} event. Assert
   * {@code sendBatch} is called with 3 {@link PushMessage} objects, each with the correct body and
   * deep-link data.
   */
  @Test
  @SuppressWarnings("unchecked")
  void step1_threeActiveTechnicianTokens_sendBatchCalledWithThreeMessages() {
    registerTechnicianToken("t1@push.com", TOKEN_1);
    registerTechnicianToken("t2@push.com", TOKEN_2);
    registerTechnicianToken("t3@push.com", TOKEN_3);

    UUID jobId = UUID.randomUUID();
    publishJobSubmitted(jobId, "PLUMBING");

    ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              verify(expoPushClient, atLeastOnce()).sendBatch(captor.capture());
              List<PushMessage> messages = captor.getValue();
              assertThat(messages).hasSize(3);

              assertThat(messages)
                  .allSatisfy(
                      msg -> {
                        assertThat(msg.body()).isEqualTo("New job: PLUMBING");
                        assertThat(msg.data()).containsEntry("jobId", jobId.toString());
                        assertThat(msg.data()).containsEntry("screen", "JobDetail");
                        assertThat(msg.channelId()).isEqualTo("jobs");
                      });

              assertThat(messages.stream().map(PushMessage::to))
                  .containsExactlyInAnyOrder(TOKEN_1, TOKEN_2, TOKEN_3);
            });
  }

  /**
   * Step 2: Mark one token stale. Publish another {@code JobSubmitted}. Assert {@code sendBatch}
   * called with only 2 messages.
   */
  @Test
  @SuppressWarnings("unchecked")
  void step2_oneTokenStale_sendBatchCalledWithTwoMessages() {
    registerTechnicianToken("t4@push.com", TOKEN_1);
    registerTechnicianToken("t5@push.com", TOKEN_2);
    registerTechnicianToken("t6@push.com", TOKEN_3);

    deviceTokenService.markStale(TOKEN_1);

    UUID jobId = UUID.randomUUID();
    publishJobSubmitted(jobId, "ELECTRICAL");

    ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
    await()
        .atMost(8, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              verify(expoPushClient, atLeastOnce()).sendBatch(captor.capture());
              List<PushMessage> messages = captor.getValue();
              assertThat(messages).hasSize(2);
              assertThat(messages.stream().map(PushMessage::to))
                  .containsExactlyInAnyOrder(TOKEN_2, TOKEN_3)
                  .doesNotContain(TOKEN_1);
            });
  }

  /**
   * Step 3: {@link ExpoPushClient} receives a {@code DeviceNotRegistered} ticket from Expo; asserts
   * that {@link DeviceTokenService#markStale(String)} is invoked for the offending token.
   *
   * <p>This test exercises {@link ExpoPushClient} directly with a mock {@link RestClient} that
   * returns a {@code DeviceNotRegistered} error ticket, bypassing the consumer so that the real
   * {@code markStale} path inside {@code ExpoPushClient.reconcileTickets()} can be verified.
   */
  @Test
  void step3_deviceNotRegisteredTicket_markStaleCalledForOffendingToken() throws Exception {
    registerTechnicianToken("t7@push.com", TOKEN_1);

    String expoResponse =
        objectMapper.writeValueAsString(
            Map.of(
                "data",
                List.of(
                    Map.of("status", "error", "details", Map.of("error", "DeviceNotRegistered")))));

    RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
    when(responseSpec.body(String.class)).thenReturn(expoResponse);

    RestClient.RequestBodySpec bodySpec =
        org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(bodySpec.body(anyList())).thenReturn(bodySpec);

    RestClient.RequestBodyUriSpec uriSpec =
        org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
    when(uriSpec.body(anyList())).thenReturn(bodySpec);

    RestClient mockRestClient = org.mockito.Mockito.mock(RestClient.class);
    when(mockRestClient.post()).thenReturn(uriSpec);

    ExpoPushProperties props =
        new ExpoPushProperties("https://exp.host/--/api/v2/push/send", 100, 3, 1000L);
    ExpoPushClient client =
        new ExpoPushClient(mockRestClient, objectMapper, deviceTokenService, props);

    List<PushTicket> tickets =
        client.sendBatch(List.of(new PushMessage(TOKEN_1, "Test", "body", Map.of())));

    assertThat(tickets).hasSize(1);
    assertThat(tickets.get(0).status()).isEqualTo("error");

    verify(deviceTokenService, atLeastOnce()).markStale(TOKEN_1);
  }

  /**
   * Batching test: 101 messages are split into 2 HTTP requests (batch of 100 + batch of 1).
   *
   * <p>Uses a mock {@link RestClient} configured to return an empty data array, verifying that
   * exactly 2 HTTP POST calls are made.
   */
  @Test
  void batchingTest_101Messages_splitIntoTwoBatches() throws Exception {
    String emptyResponse = objectMapper.writeValueAsString(Map.of("data", List.of()));

    RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
    when(responseSpec.body(String.class)).thenReturn(emptyResponse);

    RestClient.RequestBodySpec bodySpec =
        org.mockito.Mockito.mock(RestClient.RequestBodySpec.class);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(bodySpec.body(anyList())).thenReturn(bodySpec);

    RestClient.RequestBodyUriSpec uriSpec =
        org.mockito.Mockito.mock(RestClient.RequestBodyUriSpec.class);
    when(uriSpec.body(anyList())).thenReturn(bodySpec);

    RestClient mockRestClient = org.mockito.Mockito.mock(RestClient.class);
    when(mockRestClient.post()).thenReturn(uriSpec);

    ExpoPushProperties props =
        new ExpoPushProperties("https://exp.host/--/api/v2/push/send", 100, 1, 0L);
    ExpoPushClient client =
        new ExpoPushClient(mockRestClient, objectMapper, deviceTokenService, props);

    List<PushMessage> messages =
        java.util.stream.IntStream.rangeClosed(1, 101)
            .mapToObj(
                i -> new PushMessage("ExponentPushToken[device-" + i + "]", "T", "B", Map.of()))
            .collect(java.util.stream.Collectors.toList());

    client.sendBatch(messages);

    verify(mockRestClient, org.mockito.Mockito.times(2)).post();
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private User registerTechnicianToken(String email, String expoToken) {
    User user = userService.createUser(tenantId, email, "Pass1234!", UserRole.TECHNICIAN, "Tech");
    deviceTokenService.register(user.getId(), tenantId, expoToken, DevicePlatform.ANDROID);
    return user;
  }

  private void publishJobSubmitted(UUID jobId, String serviceType) {
    TenantContext.set(tenantId);
    txTemplate.execute(
        status -> {
          publisher.publish(
              "JobSubmitted",
              "Job",
              jobId,
              new JobSubmitted(UUID.randomUUID(), tenantId, jobId, null, serviceType, null));
          return null;
        });
    TenantContext.clear();
  }
}
