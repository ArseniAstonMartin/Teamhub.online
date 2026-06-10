package com.teamhub.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.schemas.FieldDefinition;
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
 * End-to-end HTTP integration tests for {@code /api/v1/automations} (PRD §16.2, §5.3; TASK-051).
 *
 * <p>Test steps (verbatim from tasks.json):
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_adminCreatesLocksmithRule_returns201AndPersisted()}): Admin
 *       creates the automotive locksmith rule from PRD §16.4; rule persisted.
 *   <li><b>Step 2</b> ({@link #step2_dispatcherCreatesRule_returns403()}): Dispatcher attempts
 *       create → 403.
 *   <li><b>Step 3</b> ({@link #step3_updateRuleWithUnknownMatchRulesKey_returns422()}): Update a
 *       rule with a {@code match_rules.make} referencing a field not in the active schema → 422.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class AutomationRuleIT {

  private static final String BASE_URL = "/api/v1/automations";

  private static final String SCHEMAS_URL = "/api/v1/schemas";

  /**
   * Schema field keys for the automotive-locksmith fixture (PRD §16.4). "year_range" is included
   * because PRD §16.2 uses it as a match_rules key.
   */
  private static final List<String> LOCKSMITH_FIELD_KEYS =
      List.of(
          "service_type",
          "make",
          "model",
          "year",
          "year_range",
          "equipment",
          "description",
          "duration_min",
          "duration_max",
          "difficulty",
          "price_usd");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private TenantRepository tenantRepository;

  private Tenant tenant;

  private UUID adminUserId;

  private String adminToken;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    String ownerEmail = "auto+" + UUID.randomUUID() + "@test.com";
    tenant = tenantService.provision("Auto Rules Co " + UUID.randomUUID(), ownerEmail);
    tenant.setPlan(Plan.STARTER);
    tenantRepository.save(tenant);
    adminUserId =
        userRepository
            .findByEmail(ownerEmail)
            .orElseThrow(() -> new IllegalStateException("Admin user not found after provision"))
            .getId();
    adminToken = mintToken(tenant.getId(), UserRole.ADMIN.name(), adminUserId);
    dispatcherToken = mintToken(tenant.getId(), UserRole.DISPATCHER.name(), UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    jdbc.update("DELETE FROM automation_rules WHERE tenant_id = ?", tid);
    // audit_log rows reference users.id — must be removed before users
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM appointment_schemas WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
  }

  // =========================================================================
  // Step 1 — Admin creates the automotive locksmith rule from PRD §16.4
  // =========================================================================

  /**
   * TASK-051 Step 1: Admin publishes a schema that covers all locksmith field keys, then creates
   * the automotive locksmith rule from PRD §16.4. Expects 201 and the rule persisted in {@code
   * automation_rules}.
   */
  @Test
  void step1_adminCreatesLocksmithRule_returns201AndPersisted() throws Exception {
    publishSchema(adminToken, LOCKSMITH_FIELD_KEYS);

    Map<String, Object> request =
        Map.of(
            "name",
            "All Keys Lost — Mercedes-Benz C-Class",
            "trigger_fields",
            List.of("service_type", "make", "model", "year"),
            "match_rules",
            Map.of(
                "service_type", "All Keys Lost",
                "make", "Mercedes-Benz",
                "model", "C-Class",
                "year_range", "2008-2014"),
            "actions",
            Map.of(
                "equipment",
                List.of("Autel Programmer", "Autel G-Box"),
                "description",
                "Standard AKL procedure for C-Class 2008-2014",
                "duration_min",
                30,
                "duration_max",
                120,
                "difficulty",
                "medium",
                "price_usd",
                500),
            "active",
            true,
            "priority",
            100);

    MvcResult result =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("All Keys Lost — Mercedes-Benz C-Class"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.priority").value(100))
            .andExpect(jsonPath("$.match_rules.make").value("Mercedes-Benz"))
            .andExpect(jsonPath("$.actions.difficulty").value("medium"))
            .andReturn();

    String ruleId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM automation_rules WHERE id = ?::uuid AND tenant_id = ?",
            Integer.class,
            ruleId,
            tenant.getId());
    assertThat(count).isEqualTo(1);
  }

  // =========================================================================
  // Step 2 — Dispatcher attempts create → 403
  // =========================================================================

  /**
   * TASK-051 Step 2: DISPATCHER does not hold {@code PERM_automation.edit}; {@code POST
   * /api/v1/automations} must return 403 FORBIDDEN (PRD §5.3).
   */
  @Test
  void step2_dispatcherCreatesRule_returns403() throws Exception {
    Map<String, Object> request = Map.of("name", "Some Rule", "active", true);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // =========================================================================
  // Step 3 — Update rule with unknown match_rules key → 422
  // =========================================================================

  /**
   * TASK-051 Step 3: Publish a schema that does NOT contain "make", create a valid rule (without
   * "make" in match_rules), then PATCH the rule with {@code match_rules.make} → 422
   * AUTOMATION_RULE_INVALID.
   */
  @Test
  void step3_updateRuleWithUnknownMatchRulesKey_returns422() throws Exception {
    // Schema without "make"
    publishSchema(adminToken, List.of("service_type", "model", "year", "equipment", "description"));

    Map<String, Object> createRequest =
        Map.of(
            "name",
            "Rule Without Make",
            "match_rules",
            Map.of("service_type", "All Keys Lost"),
            "actions",
            Map.of("equipment", List.of("Generic Tool")),
            "active",
            true);

    MvcResult created =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    String ruleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    // PATCH: include "make" which is NOT in the active schema → 422
    Map<String, Object> updateRequest =
        Map.of("match_rules", Map.of("service_type", "All Keys Lost", "make", "Mercedes-Benz"));

    mockMvc
        .perform(
            patch(BASE_URL + "/" + ruleId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value(AutomationRuleValidationException.CODE));
  }

  // =========================================================================
  // Additional — list returns active rules; archive removes from list
  // =========================================================================

  @Test
  void list_asAdmin_returnsActiveRules() throws Exception {
    publishSchema(adminToken, List.of("service_type", "model"));

    Map<String, Object> request =
        Map.of("name", "Test Rule", "match_rules", Map.of("service_type", "Rekey"), "active", true);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE_URL)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Test Rule"));
  }

  @Test
  void delete_asAdmin_softDeletesRule() throws Exception {
    publishSchema(adminToken, List.of("service_type"));

    Map<String, Object> createRequest =
        Map.of(
            "name",
            "Rule To Archive",
            "match_rules",
            Map.of("service_type", "Test"),
            "active",
            true);

    MvcResult created =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    String ruleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    BASE_URL + "/" + ruleId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM automation_rules WHERE id = ?::uuid AND archived_at IS NULL",
            Integer.class,
            ruleId);
    assertThat(count).isZero();
  }

  @Test
  void create_withUnknownActionKey_returns422() throws Exception {
    publishSchema(adminToken, List.of("service_type", "model"));

    Map<String, Object> request =
        Map.of(
            "name",
            "Bad Actions Rule",
            "match_rules",
            Map.of("service_type", "Rekey"),
            "actions",
            Map.of("nonexistent_field", "value"),
            "active",
            true);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value(AutomationRuleValidationException.CODE));
  }

  // =========================================================================
  // GET by id — happy path and 404
  // =========================================================================

  @Test
  void get_byId_asAdmin_returns200() throws Exception {
    publishSchema(adminToken, List.of("service_type"));

    Map<String, Object> createRequest =
        Map.of(
            "name", "Rule To Get", "match_rules", Map.of("service_type", "Rekey"), "active", true);

    MvcResult created =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    String ruleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    BASE_URL + "/" + ruleId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ruleId))
        .andExpect(jsonPath("$.name").value("Rule To Get"));
  }

  @Test
  void get_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    BASE_URL + "/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(AutomationRuleNotFoundException.CODE));
  }

  // =========================================================================
  // PATCH update — happy path
  // =========================================================================

  @Test
  void update_existingRule_asAdmin_returns200() throws Exception {
    publishSchema(adminToken, List.of("service_type", "model"));

    Map<String, Object> createRequest =
        Map.of(
            "name",
            "Original Name",
            "match_rules",
            Map.of("service_type", "Rekey"),
            "active",
            true,
            "priority",
            100);

    MvcResult created =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    String ruleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    Map<String, Object> updateRequest =
        Map.of("name", "Updated Name", "active", false, "priority", 50);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + ruleId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.priority").value(50));
  }

  // =========================================================================
  // Validator edge cases — unknown trigger_fields key; no active schema
  // =========================================================================

  @Test
  void create_withUnknownTriggerFieldsKey_returns422() throws Exception {
    publishSchema(adminToken, List.of("service_type"));

    Map<String, Object> request =
        Map.of(
            "name", "Bad Trigger Rule", "trigger_fields", List.of("unknown_field"), "active", true);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value(AutomationRuleValidationException.CODE));
  }

  @Test
  void create_withNoActiveSchema_returns201() throws Exception {
    // No schema published: validator skips, rule is allowed through
    Map<String, Object> request =
        Map.of(
            "name",
            "No-Schema Rule",
            "match_rules",
            Map.of("any_field", "any_value"),
            "active",
            true);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Publishes a schema containing one simple {@code text} field per key in {@code fieldKeys} via
   * {@code POST /api/v1/schemas}.
   */
  private void publishSchema(String token, List<String> fieldKeys) throws Exception {
    List<FieldDefinition> fields =
        fieldKeys.stream()
            .map(
                k ->
                    new FieldDefinition(
                        k,
                        k.replace("_", " "),
                        "text",
                        false,
                        fieldKeys.indexOf(k) + 1,
                        null,
                        null,
                        null,
                        null))
            .toList();

    PublishSchemaRequest schemaReq = new PublishSchemaRequest("Automotive Locksmith Job", fields);

    mockMvc
        .perform(
            post(SCHEMAS_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(schemaReq)))
        .andExpect(status().isCreated());
  }

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
