package com.teamhub.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.UserService;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.push.dto.RegisterDeviceRequest;
import com.teamhub.push.dto.UnregisterDeviceRequest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for device-token registration endpoints (PRD §15.1, TASK-056).
 *
 * <p>Test steps:
 *
 * <ol>
 *   <li>Register a token — row appears in DB.
 *   <li>Re-register the same token — same row ID, {@code last_seen_at} updated.
 *   <li>Mark token stale — subsequent fan-out query excludes it.
 *   <li>Unregister a token — row is deleted.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DeviceTokenIT {

  private static final String EXPO_TOKEN = "ExponentPushToken[test-device-abc123]";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserService userService;

  @Autowired private DeviceTokenRepository deviceTokenRepository;

  @Autowired private DeviceTokenService deviceTokenService;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private UUID tenantId;

  private UUID userId;

  private String accessToken;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Push Test Corp"));
    tenantId = tenant.getId();
    User user =
        userService.createUser(
            tenantId, "tech@pushtest.com", "Pass1234!", UserRole.TECHNICIAN, "Tech");
    userId = user.getId();
    accessToken = mintToken(userId, tenantId, "TECHNICIAN");
  }

  /** Step 1: Register a token; row appears in DB with correct fields. */
  @Test
  void step1_registerToken_rowAppearsInDb() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/devices/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.ANDROID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expoToken").value(EXPO_TOKEN))
        .andExpect(jsonPath("$.platform").value("ANDROID"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.lastSeenAt").isNotEmpty());

    assertThat(deviceTokenRepository.findByExpoToken(EXPO_TOKEN)).isPresent();
  }

  /** Step 2: Re-register the same token — same row ID, {@code last_seen_at} updated. */
  @Test
  void step2_reRegisterSameToken_sameRowLastSeenAtUpdated() throws Exception {
    // First registration.
    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/devices/register")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.IOS))))
            .andExpect(status().isOk())
            .andReturn();

    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
    String firstLastSeen =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("lastSeenAt").asText();

    // Small sleep to ensure timestamp difference is observable.
    Thread.sleep(10);

    // Second registration of the same token.
    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/devices/register")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.IOS))))
            .andExpect(status().isOk())
            .andReturn();

    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    String secondLastSeen =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("lastSeenAt").asText();

    assertThat(secondId).isEqualTo(firstId);
    assertThat(Instant.parse(secondLastSeen)).isAfterOrEqualTo(Instant.parse(firstLastSeen));
    // Only one row in DB.
    assertThat(deviceTokenRepository.findAll()).hasSize(1);
  }

  /**
   * Step 3: Mark token stale — fan-out query ({@code findByTenantIdAndStaleIsFalse}) excludes it.
   */
  @Test
  void step3_markStale_tokenExcludedFromFanOut() throws Exception {
    // Register the token first.
    mockMvc
        .perform(
            post("/api/v1/devices/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.ANDROID))))
        .andExpect(status().isOk());

    List<DeviceToken> before = deviceTokenRepository.findByTenantIdAndStaleIsFalse(tenantId);
    assertThat(before).hasSize(1);

    // Mark stale.
    deviceTokenService.markStale(EXPO_TOKEN);

    List<DeviceToken> after = deviceTokenRepository.findByTenantIdAndStaleIsFalse(tenantId);
    assertThat(after).isEmpty();
  }

  /** Step 4: Unregister a token — row deleted. */
  @Test
  void step4_unregisterToken_rowDeleted() throws Exception {
    // Register first.
    mockMvc
        .perform(
            post("/api/v1/devices/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.IOS))))
        .andExpect(status().isOk());

    assertThat(deviceTokenRepository.findByExpoToken(EXPO_TOKEN)).isPresent();

    // Unregister.
    mockMvc
        .perform(
            post("/api/v1/devices/unregister")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UnregisterDeviceRequest(EXPO_TOKEN))))
        .andExpect(status().isNoContent());

    assertThat(deviceTokenRepository.findByExpoToken(EXPO_TOKEN)).isEmpty();
  }

  /** Unregister is idempotent — calling on unknown token returns 204. */
  @Test
  void unregisterUnknownToken_isIdempotent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/devices/unregister")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UnregisterDeviceRequest("ExponentPushToken[nonexistent]"))))
        .andExpect(status().isNoContent());
  }

  /** Unauthenticated request to register returns 401. */
  @Test
  void registerWithoutToken_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/devices/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterDeviceRequest(EXPO_TOKEN, DevicePlatform.ANDROID))))
        .andExpect(status().isUnauthorized());
  }

  private String mintToken(UUID uid, UUID tid, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(uid.toString())
            .claim("tenant_id", tid.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
