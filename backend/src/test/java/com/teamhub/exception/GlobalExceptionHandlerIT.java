package com.teamhub.exception;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.config.IntegrationTest;
import com.teamhub.exception.ErrorEnvelopeTestController.ValidatedRequest;
import com.teamhub.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link GlobalExceptionHandler} verifying the three TASK-025 test steps:
 *
 * <ul>
 *   <li>Step 1: invalid request body → 422 with per-field error details
 *   <li>Step 2: domain exception with code {@code JOB_ALREADY_CLAIMED} → 409 with that code in the
 *       envelope
 *   <li>Step 3: forced NPE → 500 with the envelope, no stack trace in body, full stack in logs
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Import(ErrorEnvelopeTestController.class)
@Transactional
class GlobalExceptionHandlerIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  // ---- Test Step 1: invalid request body → 422 with per-field error details ------------------

  /**
   * TASK-025 Step 1: submitting a request with blank fields triggers bean-validation failure.
   * Expects 422 Unprocessable Entity with per-field errors under {@code $.error.details.fields}.
   */
  @Test
  @WithMockUser
  void invalidRequest_returns422WithFieldErrors() throws Exception {
    ValidatedRequest invalid = new ValidatedRequest("", "");

    mockMvc
        .perform(
            post("/test/envelope/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value(GlobalExceptionHandler.VALIDATION_FAILED))
        .andExpect(jsonPath("$.error.message").isNotEmpty())
        .andExpect(jsonPath("$.error.details.fields").isArray())
        .andExpect(jsonPath("$.error.details.fields", hasSize(2)))
        .andExpect(jsonPath("$.error.details.fields[0].field").isNotEmpty())
        .andExpect(jsonPath("$.error.details.fields[0].message").isNotEmpty());
  }

  /** Confirms the 422 response contains the specific field names that failed validation. */
  @Test
  @WithMockUser
  void invalidRequest_fieldNamesArePresentInDetails() throws Exception {
    ValidatedRequest oneBlank = new ValidatedRequest("present", "");

    mockMvc
        .perform(
            post("/test/envelope/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(oneBlank)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("anotherField"));
  }

  // ---- Test Step 2: domain exception → 409 with code in envelope ----------------------------

  /**
   * TASK-025 Step 2: an endpoint throws a {@link DomainException} subclass with code {@code
   * JOB_ALREADY_CLAIMED}. Expects 409 Conflict with that exact code in the §23.4 envelope.
   */
  @Test
  @WithMockUser
  void domainException_returns409WithCode() throws Exception {
    mockMvc
        .perform(get("/test/envelope/domain-error"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("JOB_ALREADY_CLAIMED"))
        .andExpect(jsonPath("$.error.message").isNotEmpty());
  }

  // ---- Test Step 3: NPE → 500 with envelope, request_id in body, no stack trace -------------

  /**
   * TASK-025 Step 3: an endpoint throws a {@link NullPointerException}. Expects 500 with the §23.4
   * envelope; the body must contain a {@code request_id} (for log correlation) but must never
   * expose the stack trace.
   */
  @Test
  @WithMockUser
  void unexpectedException_returns500WithRequestId_noStackTrace() throws Exception {
    String responseBody =
        mockMvc
            .perform(get("/test/envelope/npe"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value(GlobalExceptionHandler.INTERNAL_SERVER_ERROR))
            .andExpect(jsonPath("$.error.message").isNotEmpty())
            .andExpect(jsonPath("$.error.details.request_id").value(not(emptyOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Verify the response body does not contain any stack-trace indicators.
    org.assertj.core.api.Assertions.assertThat(responseBody)
        .doesNotContain("NullPointerException")
        .doesNotContain("at com.teamhub")
        .doesNotContain("stackTrace");
  }

  // ---- Sanity: anonymous access still returns 401 with the security envelope ----------------

  /**
   * Guard: the security filter chain's 401 envelope (from {@link RestAuthenticationEntryPoint})
   * must still work and must not be swallowed by {@link GlobalExceptionHandler}.
   */
  @Test
  void anonymousAccess_toTestEndpoint_returns401FromFilterChain() throws Exception {
    mockMvc
        .perform(get("/test/envelope/domain-error"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value(RestAuthenticationEntryPoint.CODE));
  }
}
