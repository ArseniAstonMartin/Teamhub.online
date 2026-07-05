package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.config.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for the internal staff API (TASK-131). */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class InternalApiIT {

  private static final String DEV_EMAIL = "dev@teamhub.online";

  private static final String DEV_PASSWORD = "TeamhubDev131!";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @MockBean private JavaMailSender mailSender;

  /** Step 1: staff login returns TEAMHUB_STAFF JWT. */
  @Test
  void step1_staffLogin_returnsStaffJwt() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/internal/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = body.get("accessToken").asText();
    assertThat(token).isNotBlank();
  }

  /** CORS preflight for staff login uses the same allow-list as the tenant API. */
  @Test
  void corsPreflightOnStaffLogin_fromAllowedOrigin_isAccepted() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/internal/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  /** Step 2: provision tenant with plumber preset creates tenant + pending invite. */
  @Test
  void step2_provisionTenant_createsTenantAndInvite() throws Exception {
    String staffToken = staffToken();

    mockMvc
        .perform(
            post("/api/v1/internal/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"IT Plumbing Co","ownerEmail":"it-owner-%s@example.com","presetKey":"plumber"}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").isNotEmpty())
        .andExpect(jsonPath("$.inviteId").isNotEmpty())
        .andExpect(jsonPath("$.ownerEmail").isNotEmpty());
  }

  /** Step 4: list tenants includes provisioned tenant. */
  @Test
  void step4_listTenants_includesNewTenant() throws Exception {
    String staffToken = staffToken();
    String ownerEmail = "list-owner-" + UUID.randomUUID() + "@example.com";

    mockMvc
        .perform(
            post("/api/v1/internal/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"List Test Co","ownerEmail":"%s"}
                    """
                        .formatted(ownerEmail)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/internal/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.name == 'List Test Co')]").exists());
  }

  /** Step 5: tenant ADMIN JWT on internal tenants returns 401. */
  @Test
  void step5_tenantAdminJwtOnInternal_returns401() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String adminToken = mintTenantToken(tenantId, userId, "ADMIN");

    mockMvc
        .perform(
            get("/api/v1/internal/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isUnauthorized());
  }

  /** Step 6: non-allow-listed IP returns 404. */
  @Test
  void step6_nonAllowListedIp_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/internal/auth/login")
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.99");
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(DEV_EMAIL, DEV_PASSWORD)))
        .andExpect(status().isNotFound());
  }

  @Test
  void openApiJson_excludesInternalRoutes() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/openapi.json")).andReturn();
    JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).get("paths");
    assertThat(paths.has("/api/v1/internal/tenants")).isFalse();
    assertThat(paths.has("/api/v1/internal/auth/login")).isFalse();
  }

  @Test
  void schemaPresets_returnsPresetList() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/internal/schema-presets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].presetKey").exists())
        .andExpect(jsonPath("$[0].schemaName").exists());
  }

  private String staffToken() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/internal/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  private String mintTenantToken(UUID tenantId, UUID userId, String role) {
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
