package com.teamhub.security.rbac;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.config.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end permission enforcement through the real security filter chain (TASK-023 test steps 2
 * and 3). Tokens are minted with the application's real RS256 signing key so they pass the resource
 * server, and the {@code role} claim is translated into per-permission authorities by {@link
 * PermissionJwtAuthenticationConverter}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Import(GuardedTestController.class)
@Transactional
class RbacMethodSecurityIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private RoleRepository roleRepository;

  @Autowired private PermissionRepository permissionRepository;

  @Test
  void admin_canAccessAdminOnlyEndpoints() throws Exception {
    String token = mintToken("ADMIN");

    mockMvc
        .perform(get("/test/rbac/jobs/hard-delete").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("hard-deleted"));
    mockMvc
        .perform(get("/test/rbac/schema").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void dispatcher_isForbiddenFromHardDelete_withUniformEnvelope() throws Exception {
    // Test step 3: a permission denial returns 403 with the PRD §23.4 envelope (never 404).
    String token = mintToken("DISPATCHER");

    mockMvc
        .perform(get("/test/rbac/jobs/hard-delete").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.error.message").isNotEmpty());
  }

  @Test
  void dispatcher_canReadTenantJobs() throws Exception {
    String token = mintToken("DISPATCHER");

    mockMvc
        .perform(get("/test/rbac/jobs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void technician_isForbiddenFromSchemaEdit_withUniformEnvelope() throws Exception {
    String token = mintToken("TECHNICIAN");

    mockMvc
        .perform(get("/test/rbac/schema").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void newManagerRole_seededWithSubset_grantsAccessWithZeroControllerChanges() throws Exception {
    // Test step 2: insert a fake MANAGER role with a subset of permissions (job.read.tenant only)
    // and confirm an endpoint guarded by that permission returns 200 for the MANAGER role — proving
    // a new role requires only seed data, no controller changes (PRD §5.4).
    Permission readTenant =
        permissionRepository
            .findByCode("job.read.tenant")
            .orElseThrow(() -> new IllegalStateException("seed missing job.read.tenant"));
    Role manager = new Role();
    manager.setCode("MANAGER");
    manager.setTenantId(null);
    manager.getPermissions().add(readTenant);
    roleRepository.saveAndFlush(manager);

    String token = mintToken("MANAGER");

    // Granted permission -> 200.
    mockMvc
        .perform(get("/test/rbac/jobs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("tenant-jobs"));

    // Permission not in the subset -> 403 with the uniform envelope.
    mockMvc
        .perform(get("/test/rbac/jobs/hard-delete").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  private String mintToken(String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", UUID.randomUUID().toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
