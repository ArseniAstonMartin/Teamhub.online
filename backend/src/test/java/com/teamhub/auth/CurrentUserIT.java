package com.teamhub.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@code GET /api/v1/users/me} (TASK-132, PRD §6.2).
 *
 * <p>Test steps:
 *
 * <ol>
 *   <li>Valid JWT — email and role match logged-in user.
 *   <li>No JWT — 401.
 *   <li>Deactivated user — 403 {@code ACCOUNT_DEACTIVATED}.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class CurrentUserIT {

  private static final String TECH_EMAIL = "tech@acme.com";

  private static final String TECH_PASSWORD = "Tech-Secure1!";

  @Autowired private MockMvc mockMvc;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private UUID tenantId;

  private User technician;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Acme Field Services"));
    tenantId = tenant.getId();
    technician =
        userService.createUser(
            tenantId, TECH_EMAIL, TECH_PASSWORD, UserRole.TECHNICIAN, "Tech User");
  }

  /** Step 1: GET /api/v1/users/me with valid JWT — email and role match logged-in user. */
  @Test
  void step1_getMe_withValidJwt_returnsEmailAndRole() throws Exception {
    String token = mintToken(tenantId, technician.getId(), UserRole.TECHNICIAN.name());

    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(technician.getId().toString()))
        .andExpect(jsonPath("$.email").value(TECH_EMAIL))
        .andExpect(jsonPath("$.name").value("Tech User"))
        .andExpect(jsonPath("$.role").value("TECHNICIAN"))
        .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
  }

  /** Step 2: No JWT — 401. */
  @Test
  void step2_getMe_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  /** Step 4: Deactivated user — 403. */
  @Test
  void step4_getMe_deactivatedUser_returns403() throws Exception {
    technician.setActive(false);
    userRepository.save(technician);

    String token = mintToken(tenantId, technician.getId(), UserRole.TECHNICIAN.name());

    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value(AccountDeactivatedException.CODE));
  }

  /** Additional: DISPATCHER and ADMIN roles can call /me without extra permissions. */
  @Test
  void getMe_asDispatcher_returnsProfile() throws Exception {
    User dispatcher =
        userService.createUser(
            tenantId, "disp@acme.com", "Disp-Secure1!", UserRole.DISPATCHER, "Dispatcher");
    String token = mintToken(tenantId, dispatcher.getId(), UserRole.DISPATCHER.name());

    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("disp@acme.com"))
        .andExpect(jsonPath("$.role").value("DISPATCHER"));
  }

  private String mintToken(UUID tid, UUID userId, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", tid.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
