package com.teamhub.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.IntegrationTest;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link MdcLoggingFilter} (TASK-013 acceptance criterion 2 / test step 1).
 *
 * <p>Exercises the filter directly using {@link MockHttpServletRequest} so that MDC state can be
 * captured inside the {@link FilterChain} callback without needing to intercept actual log output.
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class MdcLoggingFilterIT {

  @Autowired private MdcLoggingFilter mdcLoggingFilter;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void requestId_isPopulatedInMdcDuringRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    List<Map<String, String>> captured = new ArrayList<>();

    FilterChain chain = (req, res) -> captured.add(MDC.getCopyOfContextMap());
    mdcLoggingFilter.doFilter(request, response, chain);

    assertThat(captured).hasSize(1);
    assertThat(captured.get(0)).containsKey(MdcLoggingFilter.MDC_REQUEST_ID);
  }

  @Test
  void xRequestIdHeader_isUsedAsRequestId_whenPresent() throws Exception {
    String customId = UUID.randomUUID().toString();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.addHeader(MdcLoggingFilter.HEADER_REQUEST_ID, customId);
    MockHttpServletResponse response = new MockHttpServletResponse();
    List<Map<String, String>> captured = new ArrayList<>();

    FilterChain chain = (req, res) -> captured.add(MDC.getCopyOfContextMap());
    mdcLoggingFilter.doFilter(request, response, chain);

    assertThat(captured.get(0).get(MdcLoggingFilter.MDC_REQUEST_ID)).isEqualTo(customId);
  }

  @Test
  void generatedRequestId_isValidUuid_whenHeaderAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    List<Map<String, String>> captured = new ArrayList<>();

    FilterChain chain = (req, res) -> captured.add(MDC.getCopyOfContextMap());
    mdcLoggingFilter.doFilter(request, response, chain);

    String requestId = captured.get(0).get(MdcLoggingFilter.MDC_REQUEST_ID);
    assertThat(requestId).isNotBlank();
    // Validates UUID format — throws if invalid.
    UUID.fromString(requestId);
  }

  @Test
  void mdc_isClearedAfterRequestCompletes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    mdcLoggingFilter.doFilter(
        request,
        response,
        (req, res) -> {
          /* no-op */
        });

    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void tenantIdAndUserId_areAlwaysPresent_evenForUnauthenticatedRequests() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    List<Map<String, String>> captured = new ArrayList<>();

    FilterChain chain = (req, res) -> captured.add(MDC.getCopyOfContextMap());
    mdcLoggingFilter.doFilter(request, response, chain);

    Map<String, String> mdc = captured.get(0);
    assertThat(mdc).containsKey(MdcLoggingFilter.MDC_TENANT_ID);
    assertThat(mdc).containsKey(MdcLoggingFilter.MDC_USER_ID);
  }
}
