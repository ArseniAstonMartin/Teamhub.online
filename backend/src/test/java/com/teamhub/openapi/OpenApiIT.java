package com.teamhub.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the SpringDoc OpenAPI 3 endpoint (TASK-026).
 *
 * <p>Verifies that {@code GET /api/v1/openapi.json} returns a valid OpenAPI 3 spec with JWT bearer
 * auth scheme, correct path coverage, and that Swagger UI is not served on the prod profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  /** Step 1 (automated): spec endpoint is public and returns a valid OpenAPI document. */
  @Test
  void openApiJson_returnsOkWithJsonContentType() throws Exception {
    mockMvc
        .perform(get("/api/v1/openapi.json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  /** Spec body contains the required {@code openapi} version key. */
  @Test
  void openApiJson_bodyContainsOpenApiKey() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/openapi.json")).andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(root.has("openapi")).as("body must contain 'openapi' key").isTrue();
    assertThat(root.get("openapi").asText()).startsWith("3.");
  }

  /** Spec paths map includes the core API routes. */
  @Test
  void openApiJson_pathsContainCoreRoutes() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/openapi.json")).andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

    JsonNode paths = root.get("paths");
    assertThat(paths).as("paths object must exist").isNotNull();
    assertThat(paths.has("/api/v1/auth/login"))
        .as("paths must include /api/v1/auth/login")
        .isTrue();
    assertThat(paths.has("/api/v1/customers")).as("paths must include /api/v1/customers").isTrue();
    assertThat(paths.has("/api/v1/jobs")).as("paths must include /api/v1/jobs").isTrue();
  }

  /** JWT Bearer security scheme is declared in components. */
  @Test
  void openApiJson_containsBearerAuthSecurityScheme() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/openapi.json")).andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

    JsonNode bearerAuth = root.path("components").path("securitySchemes").path("BearerAuth");
    assertThat(bearerAuth.isMissingNode())
        .as("components.securitySchemes.BearerAuth must be present")
        .isFalse();
    assertThat(bearerAuth.path("type").asText()).isEqualTo("http");
    assertThat(bearerAuth.path("scheme").asText()).isEqualTo("bearer");
    assertThat(bearerAuth.path("bearerFormat").asText()).isEqualTo("JWT");
  }

  /**
   * Step 2 (automated part): on the prod profile Swagger UI returns 404; the openapi.json spec is
   * still accessible.
   */
  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @Import(TestcontainersConfiguration.class)
  @ActiveProfiles("prod")
  class ProdProfileTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void swaggerUi_returnsFourOhFour_onProdProfile() throws Exception {
      mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void openApiJson_stillReturnsOk_onProdProfile() throws Exception {
      mockMvc.perform(get("/api/v1/openapi.json")).andExpect(status().isOk());
    }
  }
}
