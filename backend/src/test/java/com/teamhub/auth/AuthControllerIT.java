package com.teamhub.auth;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end HTTP tests for the auth endpoints and JWT-protected security chain (TASK-019). */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class AuthControllerIT {

  private static final String EMAIL = "owner@electrical.com";

  private static final String PASSWORD = "Sup3r-Secret!";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserService userService;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private JwtService jwtService;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  private User user;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Electrical Inc"));
    user = userService.createUser(tenant.getId(), EMAIL, PASSWORD, UserRole.DISPATCHER, "Owner");
  }

  @Test
  void login_returnsBearerTokenPair() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(greaterThan(840)))
        .andExpect(jsonPath("$.expiresIn").value(lessThanOrEqualTo(900)));
  }

  @Test
  void login_badPassword_returns401WithCode() throws Exception {
    // Reconciled in TASK-025: AuthExceptionHandler replaced by GlobalExceptionHandler.
    // InvalidCredentialsException now extends DomainException, so the response uses the
    // §23.4 envelope ($.error.code) instead of the legacy flat shape ($.code).
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void login_invalidBody_returns422WithFieldDetails() throws Exception {
    // Reconciled in TASK-025: GlobalExceptionHandler maps MethodArgumentNotValidException to 422.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("not-an-email", ""))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.details.fields").isArray());
  }

  @Test
  void protectedEndpoint_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/secure-check")).andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_withValidToken_isAuthenticated() throws Exception {
    String accessToken = jwtService.issue(user).value();

    // Authenticated request passes the security chain; no handler exists → 404, not 401/403.
    mockMvc
        .perform(get("/api/v1/secure-check").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void protectedEndpoint_withExpiredToken_returns401() throws Exception {
    String expiredToken =
        mintToken(jwtEncoder, signingKey.getKeyID(), Instant.now().minus(1, ChronoUnit.HOURS));

    mockMvc
        .perform(get("/api/v1/secure-check").header("Authorization", "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpoint_withForgedSignature_returns401() throws Exception {
    // Sign a token reusing the real kid but with an attacker-controlled key. The decoder selects
    // the verification key by kid (our public key), so the forged signature fails (PRD §6.6).
    JwtEncoder foreignEncoder = foreignEncoderWithKid(signingKey.getKeyID());
    String forged =
        mintToken(foreignEncoder, signingKey.getKeyID(), Instant.now().plusSeconds(900));

    mockMvc
        .perform(get("/api/v1/secure-check").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized());
  }

  private String mintToken(JwtEncoder encoder, String kid, Instant expiresAt) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now.minus(2, ChronoUnit.HOURS))
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim("tenant_id", user.getTenant().getId().toString())
            .claim("role", user.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static JwtEncoder foreignEncoderWithKid(String kid) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    RSAKey foreign =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .keyID(kid)
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(foreign)));
  }
}
