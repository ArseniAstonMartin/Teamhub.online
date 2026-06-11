package com.teamhub.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.customer.dto.UpdateCustomerRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * End-to-end HTTP tests for the {@code /api/v1/customers} endpoints (TASK-037).
 *
 * <p>Tokens are minted with the application's real RS256 signing key so they pass the resource
 * server, and the {@code role} claim is translated into per-permission authorities by {@link
 * com.teamhub.security.rbac.PermissionJwtAuthenticationConverter}.
 *
 * <p>Tests run against a real PostgreSQL + PostGIS container via Testcontainers. All test data is
 * created within the test transaction and rolled back automatically.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class CustomerControllerIT {

  private static final String BASE_URL = "/api/v1/customers";

  private static final double HILLSBORO_LAT = 45.5229;

  private static final double HILLSBORO_LON = -122.9898;

  /** Reference E.164 phone used across test steps. */
  private static final String PHONE = "+15033334444";

  /** Expected masked form of PHONE (PRD §11.2 masking: keep first 5 + "***" + last 4). */
  private static final String MASKED_PHONE = "+1503***4444";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private UUID tenantId;

  private String dispatcherToken;

  private String technicianToken;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantService.provisionWithAdminUser("Test HVAC Co", "admin@test-hvac.com");
    tenantId = tenant.getId();
    dispatcherToken = mintToken(tenantId, UserRole.DISPATCHER.name());
    technicianToken = mintToken(tenantId, UserRole.TECHNICIAN.name());
  }

  // ---------------------------------------------------------------------------
  // Step 1 — Create same phone twice: second call returns first id, fields merged
  // ---------------------------------------------------------------------------

  @Test
  void upsert_samePhone_returnsExistingId_andMergesFields() throws Exception {
    CreateCustomerRequest first =
        new CreateCustomerRequest(
            "Alice Smith",
            PHONE,
            "alice@example.com",
            null,
            "123 Main St",
            "Hillsboro",
            "OR",
            "97124",
            HILLSBORO_LAT,
            HILLSBORO_LON,
            null);

    MvcResult r1 =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.phone").value(PHONE))
            .andExpect(jsonPath("$.name").value("Alice Smith"))
            .andReturn();

    String firstId =
        objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asText();

    // Second call — same phone, updated name and email.
    CreateCustomerRequest second =
        new CreateCustomerRequest(
            "Alice Updated",
            PHONE,
            "alice-new@example.com",
            "HVAC Corp",
            "123 Main St",
            "Hillsboro",
            "OR",
            "97124",
            HILLSBORO_LAT,
            HILLSBORO_LON,
            null);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(firstId))
        .andExpect(jsonPath("$.name").value("Alice Updated"))
        .andExpect(jsonPath("$.email").value("alice-new@example.com"))
        .andExpect(jsonPath("$.companyName").value("HVAC Corp"))
        .andExpect(jsonPath("$.phone").value(PHONE));
  }

  // ---------------------------------------------------------------------------
  // Step 2 — Archive a customer: default list excludes; include_archived=true includes
  // ---------------------------------------------------------------------------

  @Test
  void archive_excludedFromDefaultList_includedWithFlag() throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Bob Jones",
            "+15039998888",
            null,
            null,
            "456 Oak Ave",
            "Portland",
            "OR",
            "97201",
            45.5051,
            -122.6784,
            null);

    MvcResult r =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    String id = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();

    // Archive the customer.
    mockMvc
        .perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    // Default list: archived customer must not appear.
    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // include_archived=true: archived customer must appear.
    mockMvc
        .perform(
            get(BASE_URL + "?include_archived=true")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));
  }

  // ---------------------------------------------------------------------------
  // Step 3 — As Technician, list customers: phones masked; Dispatcher sees full phones
  // ---------------------------------------------------------------------------

  @Test
  void listCustomers_technicianSeesMaskedPhones_dispatcherSeesFullPhones() throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Carol White",
            PHONE,
            null,
            null,
            "789 Pine Rd",
            "Beaverton",
            "OR",
            "97005",
            HILLSBORO_LAT,
            HILLSBORO_LON,
            null);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    // Dispatcher sees the full phone.
    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].phone").value(PHONE));

    // Technician sees the masked phone.
    // TODO (PRD §11.2): unmask phones for customers linked to a job claimed by this technician.
    //  For now, all customers are treated as unlinked for TECHNICIAN callers — full masking
    // applied.
    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + technicianToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].phone").value(MASKED_PHONE));
  }

  // ---------------------------------------------------------------------------
  // Additional: anonymous access is rejected
  // ---------------------------------------------------------------------------

  @Test
  void listCustomers_withoutToken_returns401() throws Exception {
    mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Additional: PATCH /customers/{id} merges fields; GET /{id} returns updated record
  // ---------------------------------------------------------------------------

  @Test
  void patch_updatesNonNullFields_leavesOthersUnchanged() throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Dave Brown",
            "+15031112222",
            "dave@example.com",
            null,
            "1 Sunset Blvd",
            "Hillsboro",
            "OR",
            "97124",
            HILLSBORO_LAT,
            HILLSBORO_LON,
            null);

    MvcResult r =
        mockMvc
            .perform(
                post(BASE_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    String id = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();

    UpdateCustomerRequest patch =
        new UpdateCustomerRequest(
            "David Brown", null, "Plumbing Inc", null, null, null, null, null, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + id)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patch)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("David Brown"))
        .andExpect(jsonPath("$.companyName").value("Plumbing Inc"))
        .andExpect(jsonPath("$.email").value("dave@example.com"));
  }

  // ---------------------------------------------------------------------------
  // Additional: Technician cannot create/archive (403 with FORBIDDEN envelope)
  // ---------------------------------------------------------------------------

  @Test
  void technician_cannotCreateCustomer_returns403() throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Eve Green",
            "+15035556666",
            null,
            null,
            "2 River Rd",
            "Portland",
            "OR",
            "97201",
            45.5051,
            -122.6784,
            null);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + technicianToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // ---------------------------------------------------------------------------
  // Helper: GET /{id} returns 404 for unknown id
  // ---------------------------------------------------------------------------

  @Test
  void getById_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            get(BASE_URL + "/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  // ---------------------------------------------------------------------------
  // Helper: mint a JWT signed with the application's own key
  // ---------------------------------------------------------------------------

  private String mintToken(UUID tenantId, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
