package com.teamhub.integration.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for the Google Maps Geocoding integration (PRD §8.1, TASK-038).
 *
 * <p>Steps 1 and 2 test {@link GeocodingService} in isolation by building a fresh service instance
 * backed by a {@link MockRestServiceServer} — no real HTTP calls are made. Step 3 drives the full
 * customer-create flow with {@link SpyBean} stubbing to verify that the service layer invokes
 * geocoding and populates the {@code Customer.location} Point.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GeocodingIT {

  private static final String CUSTOMERS_URL = "/api/v1/customers";

  /** Minimal successful Google Maps Geocoding API response for Mountain View, CA. */
  private static final String MOUNTAIN_VIEW_RESPONSE =
      """
      {
        "status": "OK",
        "results": [{
          "address_components": [
            {"long_name": "1600",         "short_name": "1600",         "types": ["street_number"]},
            {"long_name": "Amphitheatre Pkwy", "short_name": "Amphitheatre Pkwy",
                "types": ["route"]},
            {"long_name": "Mountain View", "short_name": "Mountain View",
                "types": ["locality", "political"]},
            {"long_name": "California",   "short_name": "CA",
                "types": ["administrative_area_level_1", "political"]},
            {"long_name": "United States","short_name": "US",           "types": ["country"]},
            {"long_name": "94043",        "short_name": "94043",        "types": ["postal_code"]}
          ],
          "geometry": {
            "location": {"lat": 37.4220656, "lng": -122.0840897}
          }
        }]
      }
      """;

  /** Geocoding API response with ZERO_RESULTS status. */
  private static final String ZERO_RESULTS_RESPONSE =
      """
      {
        "status": "ZERO_RESULTS",
        "results": []
      }
      """;

  /** Base URL prefix matched against outgoing requests in mock server steps 1 and 2. */
  private static final String GEOCODE_BASE_URL =
      "https://maps.googleapis.com/maps/api/geocode/json";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @SpyBean private GeocodingService geocodingService;

  private UUID tenantId;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantService.provision("Geocoding Test Co", "admin@geocode-test.com");
    tenantId = tenant.getId();
    dispatcherToken = mintToken(tenantId, UserRole.DISPATCHER.name());
  }

  // ---------------------------------------------------------------------------
  // Step 1 — Successful geocode returns expected city / state / zip
  // ---------------------------------------------------------------------------

  @Test
  void geocode_knownAddress_returnsExpectedComponents() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

    mockServer
        .expect(requestTo(containsString(GEOCODE_BASE_URL)))
        .andRespond(withSuccess(MOUNTAIN_VIEW_RESPONSE, MediaType.APPLICATION_JSON));

    GoogleMapsProperties props = new GoogleMapsProperties("test-key");
    GeocodingService service = new GeocodingService(builder, props);

    GeocodeResult result = service.geocode("1600 Amphitheatre Parkway, Mountain View, CA");

    assertThat(result.city()).isEqualTo("Mountain View");
    assertThat(result.state()).isEqualTo("CA");
    assertThat(result.zip()).isEqualTo("94043");
    assertThat(result.lat()).isCloseTo(37.4220656, Offset.offset(0.0001));
    assertThat(result.lon()).isCloseTo(-122.0840897, Offset.offset(0.0001));

    mockServer.verify();
  }

  // ---------------------------------------------------------------------------
  // Step 2 — ZERO_RESULTS status throws GeocodingFailedException (400 GEOCODE_FAILED)
  // ---------------------------------------------------------------------------

  @Test
  void geocode_zeroResultsStatus_throwsGeocodingFailedException() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

    mockServer
        .expect(requestTo(containsString(GEOCODE_BASE_URL)))
        .andRespond(withSuccess(ZERO_RESULTS_RESPONSE, MediaType.APPLICATION_JSON));

    GoogleMapsProperties props = new GoogleMapsProperties("test-key");
    GeocodingService service = new GeocodingService(builder, props);

    assertThatThrownBy(() -> service.geocode("999 Fake Street, Nowhere, XX"))
        .isInstanceOf(GeocodingFailedException.class)
        .hasMessageContaining("ZERO_RESULTS");

    mockServer.verify();
  }

  // ---------------------------------------------------------------------------
  // Step 3 — Customer create with address (no lat/lon) calls GeocodingService
  //           and populates Customer.location
  // ---------------------------------------------------------------------------

  @Test
  void customerCreate_withAddressNoLatLon_geocodingInvokedAndLocationPopulated() throws Exception {
    doReturn(new GeocodeResult("Mountain View", "CA", "94043", 37.4220656, -122.0840897))
        .when(geocodingService)
        .geocode(anyString());

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Test Geocode Customer",
            "+15033337777",
            null,
            null,
            "1600 Amphitheatre Pkwy",
            null,
            null,
            null,
            null,
            null,
            "1600 Amphitheatre Pkwy, Mountain View, CA 94043");

    MvcResult result =
        mockMvc
            .perform(
                post(CUSTOMERS_URL)
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Mountain View"))
            .andExpect(jsonPath("$.state").value("CA"))
            .andExpect(jsonPath("$.zip").value("94043"))
            .andExpect(jsonPath("$.lat").value(37.4220656))
            .andExpect(jsonPath("$.lon").value(-122.0840897))
            .andReturn();

    verify(geocodingService).geocode("1600 Amphitheatre Pkwy, Mountain View, CA 94043");

    String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    customerRepository.flush();
    var customer = customerRepository.findById(UUID.fromString(id)).orElseThrow();
    assertThat(customer.getLocation()).isNotNull();
    assertThat(customer.getLocation().getX()).isCloseTo(-122.0840897, Offset.offset(0.0001));
    assertThat(customer.getLocation().getY()).isCloseTo(37.4220656, Offset.offset(0.0001));
  }

  // ---------------------------------------------------------------------------
  // Step 3b — POST /api/v1/geocode endpoint returns 422 for blank address
  // ---------------------------------------------------------------------------

  @Test
  void geocodeEndpoint_blankAddress_returns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/geocode")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  // ---------------------------------------------------------------------------
  // Step 3c — POST /api/v1/geocode endpoint calls GeocodingService and returns result
  // ---------------------------------------------------------------------------

  @Test
  void geocodeEndpoint_withStubService_returnsGeocodeResult() throws Exception {
    doReturn(new GeocodeResult("Mountain View", "CA", "94043", 37.4220656, -122.0840897))
        .when(geocodingService)
        .geocode(anyString());

    mockMvc
        .perform(
            post("/api/v1/geocode")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"1600 Amphitheatre Pkwy, Mountain View, CA\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.city").value("Mountain View"))
        .andExpect(jsonPath("$.state").value("CA"))
        .andExpect(jsonPath("$.zip").value("94043"))
        .andExpect(jsonPath("$.lat").value(37.4220656))
        .andExpect(jsonPath("$.lon").value(-122.0840897));
  }

  // ---------------------------------------------------------------------------
  // Additional — unauthenticated request to geocode endpoint returns 401
  // ---------------------------------------------------------------------------

  @Test
  void geocodeEndpoint_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/geocode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"1600 Amphitheatre Pkwy\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Additional — blank API key throws GeocodingFailedException without HTTP call
  // ---------------------------------------------------------------------------

  @Test
  void geocode_blankApiKey_throwsGeocodingFailedException() {
    GoogleMapsProperties emptyProps = new GoogleMapsProperties("");
    GeocodingService service = new GeocodingService(RestClient.builder(), emptyProps);

    assertThatThrownBy(() -> service.geocode("1600 Amphitheatre Parkway"))
        .isInstanceOf(GeocodingFailedException.class)
        .hasMessageContaining("not configured");
  }

  // ---------------------------------------------------------------------------
  // Helper: mint a JWT signed with the application's own key
  // ---------------------------------------------------------------------------

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
