package com.teamhub.logging;

import com.teamhub.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates the SLF4J MDC with per-request context fields required by PRD
 * §24.6: {@code tenant_id}, {@code user_id}, and {@code request_id}.
 *
 * <p>Registered inside the Spring Security filter chain <em>after</em> {@link
 * com.teamhub.shared.TenantContextFilter} (see {@link com.teamhub.security.SecurityConfig}), so
 * both the JWT authentication result and {@link TenantContext} are already populated when this
 * filter runs.
 *
 * <p>MDC is cleared in the {@code finally} block so that thread-pool reuse never leaks context from
 * one request to the next.
 */
public class MdcLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

  static final String MDC_REQUEST_ID = "request_id";

  static final String MDC_TENANT_ID = "tenant_id";

  static final String MDC_USER_ID = "user_id";

  static final String HEADER_REQUEST_ID = "X-Request-ID";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      populateMdc(request);
      log.debug("MDC set for {} {}", request.getMethod(), request.getRequestURI());
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }

  private void populateMdc(HttpServletRequest request) {
    String requestId = request.getHeader(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(MDC_REQUEST_ID, requestId);

    UUID tenantId = TenantContext.get();
    MDC.put(MDC_TENANT_ID, tenantId != null ? tenantId.toString() : "");

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      String sub = jwtAuth.getToken().getSubject();
      MDC.put(MDC_USER_ID, sub != null ? sub : "");
    } else {
      MDC.put(MDC_USER_ID, "");
    }
  }
}
