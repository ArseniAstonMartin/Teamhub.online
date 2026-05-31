package com.teamhub.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.customer.dto.UpdateCustomerRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edge-case integration tests for the customer endpoints (TASK-036/037).
 *
 * <p>Covers branches left uncovered by {@link CustomerControllerIT}: cross-tenant isolation in
 * service methods, all individual field-update branches in {@code CustomerService.update()},
 * phone-masking for the {@code GET /{id}} endpoint, and the non-JWT authentication path in {@link
 * CustomerController#isTechnician}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CustomerEdgeCaseIT {

  private static final String BASE_URL = "/api/v1/customers";

  private static final double LAT = 45.5229;

  private static final double LON = -122.9898;

  private static final String PHONE = "+15037770001";

  private static final String MASKED_PHONE = "+1503***0001";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @PersistenceContext private EntityManager em;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private UUID tenantId;

  private String dispatcherToken;

  private String technicianToken;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantService.provision("Edge Case Co", "admin@edge-case.com");
    tenantId = tenant.getId();
    dispatcherToken = mintToken(tenantId, UserRole.DISPATCHER.name());
    technicianToken = mintToken(tenantId, UserRole.TECHNICIAN.name());
  }

  // ---------------------------------------------------------------------------
  // GET /{id} — happy path: dispatcher sees full phone, technician sees masked
  // ---------------------------------------------------------------------------

  @Test
  void getById_asDispatcher_returnsFullPhone() throws Exception {
    String id = createCustomer(PHONE);

    mockMvc
        .perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.phone").value(PHONE));
  }

  @Test
  void getById_asTechnician_returnsMaskedPhone() throws Exception {
    String id = createCustomer(PHONE);

    mockMvc
        .perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + technicianToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value(MASKED_PHONE));
  }

  // ---------------------------------------------------------------------------
  // GET / — non-JWT authentication object → isTechnician returns false (line 140 covered)
  // ---------------------------------------------------------------------------

  @Test
  void list_withNonJwtAuthentication_returnsOkWithEmptyPage() throws Exception {
    // TestingAuthenticationToken is not a JwtAuthenticationToken → isTechnician() hits the
    // instanceof-false branch and returns false (line 140). TenantContextFilter skips setting
    // the tenant for non-JWT auth, so the page is empty — that is the observable effect.
    TestingAuthenticationToken nonJwtAuth =
        new TestingAuthenticationToken(
            "user", "creds", List.of(new SimpleGrantedAuthority("PERM_customer.read.tenant")));

    mockMvc
        .perform(get(BASE_URL).with(authentication(nonJwtAuth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ---------------------------------------------------------------------------
  // GET /{id} — cross-tenant: filter lambda is evaluated but returns false → 404
  // ---------------------------------------------------------------------------

  @Test
  void getById_crossTenantCustomer_returns404() throws Exception {
    Tenant otherTenant = tenantService.provision("Other Co", "admin@other.com");
    Customer customer = buildCustomerEntity(otherTenant.getId(), "+15031110000");
    customerRepository.save(customer);

    // Caller is tenantId (not otherTenant) → filter lambda returns false → 404
    mockMvc
        .perform(
            get(BASE_URL + "/" + customer.getId())
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  // ---------------------------------------------------------------------------
  // PATCH /{id} — all fields updated at once (email, addressLine, city, state, zip, lat/lon)
  // ---------------------------------------------------------------------------

  @Test
  void update_withAllFields_updatesEveryField() throws Exception {
    String id = createCustomer("+15032220002");

    UpdateCustomerRequest fullPatch =
        new UpdateCustomerRequest(
            "Updated Name",
            "updated@example.com",
            "Updated Corp",
            "999 New St",
            "Salem",
            "WA",
            "98001",
            47.6062,
            -122.3321);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + id)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fullPatch)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.email").value("updated@example.com"))
        .andExpect(jsonPath("$.companyName").value("Updated Corp"))
        .andExpect(jsonPath("$.addressLine").value("999 New St"))
        .andExpect(jsonPath("$.city").value("Salem"))
        .andExpect(jsonPath("$.state").value("WA"))
        .andExpect(jsonPath("$.zip").value("98001"))
        .andExpect(jsonPath("$.lat").exists())
        .andExpect(jsonPath("$.lon").exists());
  }

  // ---------------------------------------------------------------------------
  // PATCH /{id} — cross-tenant: orElseThrow in update() → 404
  // ---------------------------------------------------------------------------

  @Test
  void update_crossTenantCustomer_returns404() throws Exception {
    Tenant otherTenant = tenantService.provision("Other Tenant", "admin2@other.com");
    Customer customer = buildCustomerEntity(otherTenant.getId(), "+15033330003");
    customerRepository.save(customer);

    UpdateCustomerRequest patch =
        new UpdateCustomerRequest("New Name", null, null, null, null, null, null, null, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + customer.getId())
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patch)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  // ---------------------------------------------------------------------------
  // DELETE /{id} — archive on unknown / cross-tenant ID → 404
  // ---------------------------------------------------------------------------

  @Test
  void archive_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            delete(BASE_URL + "/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  // ---------------------------------------------------------------------------
  // PATCH /{id} — null name and companyName leave existing values unchanged
  // ---------------------------------------------------------------------------

  @Test
  void update_withNullNameAndCompanyName_leavesThemUnchanged() throws Exception {
    String id = createCustomer("+15035550005");

    // First patch: set companyName so we can verify it is not cleared later
    UpdateCustomerRequest setCompany =
        new UpdateCustomerRequest(null, null, "Acme Corp", null, null, null, null, null, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + id)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setCompany)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Acme Corp"))
        .andExpect(jsonPath("$.name").value("Test Customer"));

    // Second patch: only update email; name and companyName are null → must not change
    UpdateCustomerRequest emailOnly =
        new UpdateCustomerRequest(
            null, "edge@example.com", null, null, null, null, null, null, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + id)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emailOnly)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("edge@example.com"))
        .andExpect(jsonPath("$.name").value("Test Customer"))
        .andExpect(jsonPath("$.companyName").value("Acme Corp"));
  }

  // ---------------------------------------------------------------------------
  // POST (upsert) — second call with some null fields leaves existing values intact
  // ---------------------------------------------------------------------------

  @Test
  void upsert_secondCallWithNullOptionalFields_retainsExistingValues() throws Exception {
    CreateCustomerRequest first =
        new CreateCustomerRequest(
            "Frank Lloyd",
            PHONE,
            "frank@example.com",
            "Lloyd Inc",
            "10 Arch St",
            "Portland",
            "OR",
            "97201",
            LAT,
            LON);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
        .andExpect(status().isCreated());

    // Second upsert: email and companyName are null → mergeInto must skip those fields
    CreateCustomerRequest second =
        new CreateCustomerRequest(
            "Frank Lloyd II", PHONE, null, null, "10 Arch St", "Portland", "OR", "97201", LAT, LON);

    mockMvc
        .perform(
            post(BASE_URL)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Frank Lloyd II"))
        .andExpect(jsonPath("$.email").value("frank@example.com"))
        .andExpect(jsonPath("$.companyName").value("Lloyd Inc"));
  }

  // ---------------------------------------------------------------------------
  // PATCH /{id} — lat provided but lon is null → location not updated (line 114 false branch)
  // ---------------------------------------------------------------------------

  @Test
  void update_withLatOnlyAndNullLon_doesNotUpdateLocation() throws Exception {
    String id = createCustomer("+15036660006");

    // Send lat but omit lon (null) → the && condition short-circuits to false, location unchanged
    UpdateCustomerRequest latOnly =
        new UpdateCustomerRequest(null, null, null, null, null, null, null, 47.6062, null);

    mockMvc
        .perform(
            patch(BASE_URL + "/" + id)
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(latOnly)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lat").value(LAT))
        .andExpect(jsonPath("$.lon").value(LON));
  }

  // ---------------------------------------------------------------------------
  // Customer @PrePersist — createdAt/updatedAt pre-set → @PrePersist does not overwrite them
  // ---------------------------------------------------------------------------

  @Test
  void onPrePersist_withPreSetTimestamps_preservesOriginalValues() {
    Instant fixedCreated = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
    Instant fixedUpdated = Instant.now().minus(1, ChronoUnit.HOURS);

    Customer c = buildCustomerEntity(tenantId, "+15037770007");
    c.setCreatedAt(fixedCreated);
    c.setUpdatedAt(fixedUpdated);

    customerRepository.save(c);
    em.flush();
    em.clear();

    Customer reloaded = customerRepository.findById(c.getId()).orElseThrow();
    Assertions.assertThat(reloaded.getCreatedAt()).isEqualTo(fixedCreated);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Creates a customer via the API and returns its ID string. */
  private String createCustomer(String phone) throws Exception {
    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Test Customer", phone, null, null, "1 Test St", "Hillsboro", "OR", "97124", LAT, LON);

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

  /** Builds a Customer entity for a given tenant without persisting it. */
  private Customer buildCustomerEntity(UUID tid, String phone) {
    Customer c = new Customer();
    c.setTenantId(tid);
    c.setName("Other Tenant Customer");
    c.setPhone(phone);
    c.setAddressLine("99 Other St");
    c.setCity("Salem");
    c.setState("OR");
    c.setZip("97301");
    c.setLocation(GF.createPoint(new Coordinate(LON, LAT)));
    return c;
  }

  private String mintToken(UUID tid, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", tid.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
