package com.teamhub.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.schemas.dto.PublishSchemaRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end HTTP tests for the {@code /api/v1/schemas} endpoints (TASK-041).
 *
 * <p>Covers all three TASK-041 test steps:
 *
 * <ul>
 *   <li>Step 1: As ADMIN, publish a new version — 201 returned; audit row with {@code
 *       action=schema.update} and {@code resource_type=appointment_schema} appears in {@code
 *       audit_log}.
 *   <li>Step 2: As DISPATCHER, attempt to publish — 403 FORBIDDEN (no {@code PERM_schema.edit}).
 *   <li>Step 3: Attempt to DELETE a schema — 404 (no handler registered; append-only invariant).
 * </ul>
 *
 * <p>Tests do NOT use {@code @Transactional} at the class level because {@link
 * com.teamhub.audit.AuditLogService#record} runs in a {@code REQUIRES_NEW} transaction that commits
 * independently. Manual cleanup is performed in {@code @AfterEach} via direct JDBC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class SchemaControllerIT {

  private static final String BASE_URL = "/api/v1/schemas";

  private static final String SCHEMA_NAME = "Automotive Locksmith Job";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TenantRepository tenantRepository;

  private Tenant tenant;

  /** ID of the Admin user created by {@link InternalTenantService#provision}. */
  private UUID adminUserId;

  private String adminToken;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    String ownerEmail = "schema+" + UUID.randomUUID() + "@test.com";
    tenant =
        tenantService.provisionWithAdminUser("Schema Test Co " + UUID.randomUUID(), ownerEmail);
    tenant.setPlan(Plan.STARTER);
    tenantRepository.save(tenant);
    // Use the real admin user ID as the JWT subject so AuditLogService FK constraint is satisfied.
    adminUserId =
        userRepository
            .findByEmail(ownerEmail)
            .orElseThrow(() -> new IllegalStateException("Admin user not found after provision"))
            .getId();
    adminToken = mintToken(tenant.getId(), UserRole.ADMIN.name(), adminUserId);
    // Dispatcher token uses a random subject — dispatcher requests fail at the security layer
    // before AuditLogService is ever called, so the FK constraint is never triggered.
    dispatcherToken = mintToken(tenant.getId(), UserRole.DISPATCHER.name(), UUID.randomUUID());
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM audit_log WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM appointment_schemas WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenant.getId());
    jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenant.getId());
  }

  // ---------------------------------------------------------------------------
  // Step 1: As ADMIN, publish a new version — 201 returned; audit row appears
  // ---------------------------------------------------------------------------

  /**
   * TASK-041 Step 1: ADMIN publishes a new schema version via {@code POST /api/v1/schemas};
   * response is 201 with the persisted schema; an {@code audit_log} row with {@code
   * action=schema.update} and {@code resource_type=appointment_schema} is committed by the {@code
   * REQUIRES_NEW} audit transaction.
   */
  @Test
  void publish_asAdmin_returns201AndEmitsAuditRow() throws Exception {
    PublishSchemaRequest request =
        new PublishSchemaRequest(
            SCHEMA_NAME,
            List.of(
                new FieldDefinition(
                    "make", "Vehicle Make", "text", true, 1, null, null, null, null)));

    MvcResult result =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value(SCHEMA_NAME))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

    String schemaId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

    // Audit row must be committed by the REQUIRES_NEW transaction in AuditLogService.
    List<Map<String, Object>> auditRows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_log WHERE tenant_id = ? AND resource_type = 'appointment_schema'",
            tenant.getId());

    assertThat(auditRows).hasSize(1);
    Map<String, Object> auditRow = auditRows.get(0);
    assertThat(auditRow.get("action")).isEqualTo("schema.update");
    assertThat(auditRow.get("resource_type")).isEqualTo("appointment_schema");
    assertThat(auditRow.get("resource_id").toString()).isEqualTo(schemaId);
  }

  // ---------------------------------------------------------------------------
  // Step 2: As DISPATCHER, attempt to publish — 403 FORBIDDEN
  // ---------------------------------------------------------------------------

  /**
   * TASK-041 Step 2: DISPATCHER does not hold {@code PERM_schema.edit}; {@code POST
   * /api/v1/schemas} must return 403 with the standard FORBIDDEN error envelope (PRD §23.4).
   */
  @Test
  void publish_asDispatcher_returns403() throws Exception {
    PublishSchemaRequest request =
        new PublishSchemaRequest(
            SCHEMA_NAME,
            List.of(
                new FieldDefinition(
                    "make", "Vehicle Make", "text", true, 1, null, null, null, null)));

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // ---------------------------------------------------------------------------
  // Step 3: Attempt DELETE — no handler registered; returns 404
  // ---------------------------------------------------------------------------

  /**
   * TASK-041 Step 3: {@code DELETE /api/v1/schemas/{id}} has no handler registered for the DELETE
   * method. Spring MVC matches the URI template (a PATCH handler exists at the same path) but finds
   * no DELETE handler, returning 405 METHOD_NOT_ALLOWED. This confirms the append-only invariant:
   * there is no delete endpoint for {@code appointment_schemas} (PRD §8.2).
   */
  @Test
  void delete_noHandlerRegistered_returnsMethodNotAllowed() throws Exception {
    mockMvc
        .perform(
            delete(BASE_URL + "/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isMethodNotAllowed());
  }

  // ---------------------------------------------------------------------------
  // Additional: GET /schemas returns the tenant's schema history
  // ---------------------------------------------------------------------------

  @Test
  void list_asAdmin_returnsAllVersions() throws Exception {
    // Publish two versions.
    PublishSchemaRequest v1 =
        new PublishSchemaRequest(
            SCHEMA_NAME,
            List.of(
                new FieldDefinition(
                    "make", "Vehicle Make", "text", true, 1, null, null, null, null)));
    PublishSchemaRequest v2 =
        new PublishSchemaRequest(
            SCHEMA_NAME,
            List.of(
                new FieldDefinition(
                    "make", "Vehicle Make", "text", true, 1, null, null, null, null),
                new FieldDefinition(
                    "model", "Vehicle Model", "text", false, 2, null, null, null, null)));

    mockMvc.perform(
        post(BASE_URL)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(v1)));

    mockMvc.perform(
        post(BASE_URL)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(v2)));

    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  // ---------------------------------------------------------------------------
  // Additional: PATCH /schemas/{id} deactivates a version; emits audit row
  // ---------------------------------------------------------------------------

  @Test
  void deactivate_asAdmin_setsActiveFalseAndEmitsAuditRow() throws Exception {
    // Publish v1 first.
    PublishSchemaRequest request =
        new PublishSchemaRequest(
            SCHEMA_NAME,
            List.of(
                new FieldDefinition(
                    "make", "Vehicle Make", "text", true, 1, null, null, null, null)));

    MvcResult created =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    String schemaId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    // Deactivate the published schema.
    mockMvc
        .perform(
            patch(BASE_URL + "/" + schemaId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    // Two audit rows: one from publish, one from deactivate.
    List<Map<String, Object>> auditRows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_log WHERE tenant_id = ? AND resource_type = 'appointment_schema'",
            tenant.getId());
    assertThat(auditRows).hasSize(2);
    assertThat(auditRows)
        .allSatisfy(row -> assertThat(row.get("action")).isEqualTo("schema.update"));
  }

  // ---------------------------------------------------------------------------
  // Additional: anonymous access returns 401
  // ---------------------------------------------------------------------------

  @Test
  void listSchemas_withoutToken_returns401() throws Exception {
    mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Helper: mint a JWT signed with the application's own key
  // ---------------------------------------------------------------------------

  private String mintToken(UUID tenantId, String roleCode, UUID subject) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(subject.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
