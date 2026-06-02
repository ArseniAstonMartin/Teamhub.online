package com.teamhub.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.schemas.FieldDefinition;
import com.teamhub.schemas.dto.PublishSchemaRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * End-to-end HTTP integration tests for {@code POST /api/v1/automations/evaluate} (PRD §16.3;
 * TASK-052).
 *
 * <p>Test steps (verbatim from tasks.json):
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_locksmithDraftAllFields_fillsAllExpectedActions()}): PRD §16.4
 *       example — dispatcher submits full draft → engine fills equipment, description, duration,
 *       difficulty, price.
 *   <li><b>Step 2</b> ({@link #step2_descriptionManuallySet_engineSkipsDescription_fillsOthers()}):
 *       Same draft but {@code description} in manuallySetFields → engine fills everything except
 *       description.
 *   <li><b>Step 3</b> ({@link
 *       #step3_twoRules_differentPriorities_higherWinsOnOverlap_lowerFillsNonOverlap()}): Two
 *       matching rules, different priorities, overlapping on one field → higher-priority wins on
 *       that field; lower-priority fills non-overlapping fields.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutomationEngineIT {

  private static final String AUTOMATIONS_URL = "/api/v1/automations";

  private static final String EVALUATE_URL = AUTOMATIONS_URL + "/evaluate";

  private static final String SCHEMAS_URL = "/api/v1/schemas";

  /**
   * Field keys for the PRD §16.4 automotive locksmith fixture — must match the schema used in
   * {@link AutomationRuleIT}.
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

  private Tenant tenant;

  private String adminToken;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    String ownerEmail = "engine+" + UUID.randomUUID() + "@test.com";
    tenant = tenantService.provision("Engine Co " + UUID.randomUUID(), ownerEmail);
    UUID adminUserId = userRepository.findByEmail(ownerEmail).orElseThrow().getId();
    adminToken = mintToken(tenant.getId(), UserRole.ADMIN.name(), adminUserId);
    dispatcherToken = mintToken(tenant.getId(), UserRole.DISPATCHER.name(), UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    jdbc.update("DELETE FROM automation_rules WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM appointment_schemas WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
    jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
  }

  // =========================================================================
  // Step 1 — PRD §16.4 full draft, no manually-set fields
  // =========================================================================

  /**
   * TASK-052 Step 1: Seed the PRD §16.4 automotive locksmith rule. POST evaluate with all four
   * trigger fields set and {@code manually_set_fields = []}. Expects {@code suggested_patch} to
   * contain equipment, description, duration_min, duration_max, difficulty, and price_usd.
   */
  @Test
  void step1_locksmithDraftAllFields_fillsAllExpectedActions() throws Exception {
    publishSchema(adminToken, LOCKSMITH_FIELD_KEYS);
    createLocksmithRule(adminToken, 100);

    Map<String, Object> request =
        Map.of(
            "draft_fields",
            Map.of(
                "service_type", "All Keys Lost",
                "make", "Mercedes-Benz",
                "model", "C-Class",
                "year", "2010"),
            "manually_set_fields",
            List.of());

    MvcResult result =
        mockMvc
            .perform(
                post(EVALUATE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggested_patch.equipment").isArray())
            .andExpect(jsonPath("$.suggested_patch.description").isString())
            .andExpect(jsonPath("$.suggested_patch.duration_min").value(30))
            .andExpect(jsonPath("$.suggested_patch.duration_max").value(120))
            .andExpect(jsonPath("$.suggested_patch.difficulty").value("medium"))
            .andExpect(jsonPath("$.suggested_patch.price_usd").value(500))
            .andExpect(jsonPath("$.auto_filled_fields").isArray())
            .andReturn();

    JsonNode patch =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("suggested_patch");
    assertThat(patch.has("equipment")).isTrue();
    assertThat(patch.has("description")).isTrue();
    assertThat(patch.has("duration_min")).isTrue();
    assertThat(patch.has("duration_max")).isTrue();
    assertThat(patch.has("difficulty")).isTrue();
    assertThat(patch.has("price_usd")).isTrue();

    JsonNode autoFilled =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("auto_filled_fields");
    assertThat(autoFilled.isArray()).isTrue();
    assertThat(autoFilled.size()).isGreaterThanOrEqualTo(6);
  }

  // =========================================================================
  // Step 2 — description manually set → engine skips it
  // =========================================================================

  /**
   * TASK-052 Step 2: Same draft as Step 1 but {@code description} is in {@code
   * manually_set_fields}. Expects {@code suggested_patch} to contain all Step 1 fields EXCEPT
   * {@code description}.
   */
  @Test
  void step2_descriptionManuallySet_engineSkipsDescription_fillsOthers() throws Exception {
    publishSchema(adminToken, LOCKSMITH_FIELD_KEYS);
    createLocksmithRule(adminToken, 100);

    Map<String, Object> request =
        Map.of(
            "draft_fields",
            Map.of(
                "service_type", "All Keys Lost",
                "make", "Mercedes-Benz",
                "model", "C-Class",
                "year", "2010"),
            "manually_set_fields",
            Set.of("description"));

    MvcResult result =
        mockMvc
            .perform(
                post(EVALUATE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggested_patch.equipment").isArray())
            .andExpect(jsonPath("$.suggested_patch.duration_min").value(30))
            .andExpect(jsonPath("$.suggested_patch.duration_max").value(120))
            .andExpect(jsonPath("$.suggested_patch.difficulty").value("medium"))
            .andExpect(jsonPath("$.suggested_patch.price_usd").value(500))
            .andReturn();

    JsonNode patch =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("suggested_patch");
    assertThat(patch.has("description")).isFalse();
    assertThat(patch.has("equipment")).isTrue();
    assertThat(patch.has("difficulty")).isTrue();
  }

  // =========================================================================
  // Step 3 — Two rules, different priorities, overlapping on one field
  // =========================================================================

  /**
   * TASK-052 Step 3: Seed two rules that both match the same trigger condition. Rule A (priority
   * 10) sets {@code difficulty = "hard"} and {@code duration_min = 60}. Rule B (priority 50) sets
   * {@code difficulty = "easy"} and {@code price_usd = 999}. Expected outcome:
   *
   * <ul>
   *   <li>{@code difficulty} = "hard" (Rule A wins — lower priority number = higher precedence)
   *   <li>{@code duration_min} = 60 (only Rule A sets it)
   *   <li>{@code price_usd} = 999 (only Rule B sets it — Rule B fills non-overlapping fields)
   * </ul>
   */
  @Test
  void step3_twoRules_differentPriorities_higherWinsOnOverlap_lowerFillsNonOverlap()
      throws Exception {
    List<String> fieldKeys =
        List.of("service_type", "make", "difficulty", "duration_min", "price_usd");
    publishSchema(adminToken, fieldKeys);

    // Rule A: priority 10 (higher precedence), sets difficulty + duration_min
    createRule(
        adminToken,
        "Rule A - High Priority",
        List.of("service_type"),
        Map.of("service_type", "Rekey"),
        Map.of("difficulty", "hard", "duration_min", 60),
        10);

    // Rule B: priority 50 (lower precedence), sets difficulty (overlap) + price_usd (unique)
    createRule(
        adminToken,
        "Rule B - Low Priority",
        List.of("service_type"),
        Map.of("service_type", "Rekey"),
        Map.of("difficulty", "easy", "price_usd", 999),
        50);

    Map<String, Object> request =
        Map.of(
            "draft_fields", Map.of("service_type", "Rekey"),
            "manually_set_fields", List.of());

    MvcResult result =
        mockMvc
            .perform(
                post(EVALUATE_URL)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode patch =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("suggested_patch");

    // Rule A (priority 10) wins on difficulty
    assertThat(patch.get("difficulty").asText()).isEqualTo("hard");
    // Rule A fills duration_min
    assertThat(patch.get("duration_min").asInt()).isEqualTo(60);
    // Rule B fills price_usd (non-overlapping field)
    assertThat(patch.get("price_usd").asInt()).isEqualTo(999);
  }

  // =========================================================================
  // Additional — technician cannot call evaluate (no PERM_job.create)
  // =========================================================================

  @Test
  void evaluate_asTechnician_returns403() throws Exception {
    String technicianToken =
        mintToken(tenant.getId(), UserRole.TECHNICIAN.name(), UUID.randomUUID());

    Map<String, Object> request =
        Map.of(
            "draft_fields", Map.of("service_type", "Rekey"),
            "manually_set_fields", List.of());

    mockMvc
        .perform(
            post(EVALUATE_URL)
                .header("Authorization", "Bearer " + technicianToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private String createLocksmithRule(String token, int priority) throws Exception {
    return createRule(
        token,
        "All Keys Lost — Mercedes-Benz C-Class",
        List.of("service_type", "make", "model", "year"),
        Map.of(
            "service_type", "All Keys Lost",
            "make", "Mercedes-Benz",
            "model", "C-Class",
            "year_range", "2008-2014"),
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
        priority);
  }

  private String createRule(
      String token,
      String name,
      List<String> triggerFields,
      Map<String, Object> matchRules,
      Map<String, Object> actions,
      int priority)
      throws Exception {
    Map<String, Object> request =
        Map.of(
            "name", name,
            "trigger_fields", triggerFields,
            "match_rules", matchRules,
            "actions", actions,
            "active", true,
            "priority", priority);

    MvcResult result =
        mockMvc
            .perform(
                post(AUTOMATIONS_URL)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

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

    PublishSchemaRequest schemaReq = new PublishSchemaRequest("Locksmith Job", fields);

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
