package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.dto.CreateCustomerRequest;
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

/** Integration tests for {@code POST /api/v1/customers/{id}/restore} (TASK-126). */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class CustomerRestoreIT {

  private static final String BASE_URL = "/api/v1/customers";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private UUID tenantId;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    Tenant tenant =
        tenantService.provisionWithAdminUser("Restore Test HVAC", "admin@restore-test.com");
    tenantId = tenant.getId();
    dispatcherToken = mintToken(tenantId, UserRole.DISPATCHER.name());
  }

  // Step 1 — Archive then restore: archived_at is null in DB
  @Test
  void step1_archiveThenRestore_archivedAtIsNull() throws Exception {
    String id = createCustomer("Frank Restore", "+15037778888");

    mockMvc
        .perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    assertThat(customerRepository.findById(UUID.fromString(id)).orElseThrow().getArchivedAt())
        .isNotNull();

    mockMvc
        .perform(
            post(BASE_URL + "/" + id + "/restore")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.archivedAt").doesNotExist());

    assertThat(customerRepository.findById(UUID.fromString(id)).orElseThrow().getArchivedAt())
        .isNull();
  }

  // Step 2 — List with include_archived=true: restored customer appears in default list
  @Test
  void step2_restore_reappearsInDefaultList() throws Exception {
    String id = createCustomer("Grace Restore", "+15036667777");

    mockMvc
        .perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(BASE_URL + "/" + id + "/restore")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").doesNotExist());

    mockMvc
        .perform(get(BASE_URL).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id))
        .andExpect(jsonPath("$.content[0].archivedAt").doesNotExist());
  }

  // Step 3 — Detail GET after restore returns active customer
  @Test
  void step3_detailAfterRestore_returnsActiveCustomer() throws Exception {
    String id = createCustomer("Henry Restore", "+15035556666");

    mockMvc
        .perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(BASE_URL + "/" + id + "/restore")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.archivedAt").doesNotExist());
  }

  // Step 4 — Restore on non-archived customer → 404
  @Test
  void step4_restoreOnNonArchived_returns404() throws Exception {
    String id = createCustomer("Ivy Active", "+15034445555");

    mockMvc
        .perform(
            post(BASE_URL + "/" + id + "/restore")
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  @Test
  void restore_crossTenantCustomer_returns404() throws Exception {
    String id = createCustomer("Jack Cross", "+15033332222");

    Tenant otherTenant = tenantService.provisionWithAdminUser("Other HVAC", "admin@other.com");
    String otherToken = mintToken(otherTenant.getId(), UserRole.DISPATCHER.name());

    mockMvc
        .perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(BASE_URL + "/" + id + "/restore").header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  private String createCustomer(String name, String phone) throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            name,
            phone,
            null,
            null,
            "100 Test St",
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

    return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

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
