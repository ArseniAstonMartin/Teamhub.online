package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for user last-login tracking (TASK-129). */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class UserLastLoginIT {

  private static final String ADMIN_EMAIL = "admin@acme.com";

  private static final String ADMIN_PASSWORD = "Admin-Secure1!";

  private static final String INVITED_EMAIL = "invited@acme.com";

  private static final String INVITED_PASSWORD = "Invited-Secure1!";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  private UUID tenantId;

  private User adminUser;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Acme Field Services"));
    tenantId = tenant.getId();
    adminUser =
        userService.createUser(tenantId, ADMIN_EMAIL, ADMIN_PASSWORD, UserRole.ADMIN, "Admin User");
  }

  /** Step 1: Login then GET /api/v1/users — caller's {@code lastLoginAt} within last 5 s. */
  @Test
  void step1_loginThenListUsers_lastLoginAtWithinFiveSeconds() throws Exception {
    Instant beforeLogin = Instant.now();

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken =
        objectMapper
            .readTree(loginResult.getResponse().getContentAsString())
            .get("accessToken")
            .asText();

    MvcResult listResult =
        mockMvc
            .perform(
                get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andReturn();

    JsonNode users = objectMapper.readTree(listResult.getResponse().getContentAsString());
    JsonNode adminRow = null;
    for (JsonNode row : users.get("content")) {
      if (ADMIN_EMAIL.equals(row.get("email").asText())) {
        adminRow = row;
        break;
      }
    }
    assertThat(adminRow).isNotNull();
    assertThat(adminRow.get("lastLoginAt").isNull()).isFalse();

    Instant lastLoginAt = Instant.parse(adminRow.get("lastLoginAt").asText());
    assertThat(lastLoginAt).isAfterOrEqualTo(beforeLogin.minus(Duration.ofSeconds(1)));
    assertThat(lastLoginAt).isBeforeOrEqualTo(Instant.now().plus(Duration.ofSeconds(1)));
    assertThat(Duration.between(lastLoginAt, Instant.now()).getSeconds()).isLessThanOrEqualTo(5);
  }

  /**
   * Step 2: Invited-but-never-logged-in user — {@code lastLoginAt} is null in GET /api/v1/users.
   */
  @Test
  void step2_neverLoggedInUser_lastLoginAtIsNull() throws Exception {
    userService.createUser(
        tenantId, INVITED_EMAIL, INVITED_PASSWORD, UserRole.DISPATCHER, "Invited User");

    User invited =
        userRepository.findByEmail(INVITED_EMAIL).orElseThrow(() -> new IllegalStateException());
    assertThat(invited.getLastLoginAt()).isNull();

    String adminToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

    mockMvc
        .perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.email == '" + INVITED_EMAIL + "')].lastLoginAt")
                .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn();

    return objectMapper
        .readTree(loginResult.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }
}
