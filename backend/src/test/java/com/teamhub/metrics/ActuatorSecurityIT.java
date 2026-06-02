package com.teamhub.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamhub.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for Actuator endpoint security (TASK-014).
 *
 * <p>Verifies that {@code /actuator/prometheus} is protected by HTTP Basic auth while {@code
 * /actuator/health} and {@code /actuator/info} remain public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActuatorSecurityIT {

  private static final String ACTUATOR_USER = "actuator";

  private static final String ACTUATOR_PASS = "actuator-secret";

  @Autowired private MockMvc mockMvc;

  /**
   * Step 1: {@code GET /actuator/prometheus} without credentials returns 401 or 403 (protected).
   */
  @Test
  void step1_prometheus_withoutAuth_returns401Or403() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(
            result -> {
              int status = result.getResponse().getStatus();
              assertThat(status).isIn(401, 403);
            });
  }

  /**
   * Step 2: {@code GET /actuator/prometheus} with valid Basic auth returns 200 and contains JVM
   * metrics in Prometheus exposition format.
   */
  @Test
  void step2_prometheus_withValidAuth_returns200AndContainsJvmMetrics() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/actuator/prometheus").with(httpBasic(ACTUATOR_USER, ACTUATOR_PASS)))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("jvm_memory_used_bytes");
  }

  /** Step 3: {@code GET /actuator/health} without auth returns 200 (public endpoint). */
  @Test
  void step3_health_withoutAuth_returns200() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  /** {@code GET /actuator/info} without auth returns 200 (public endpoint). */
  @Test
  void info_withoutAuth_returns200() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
  }
}
