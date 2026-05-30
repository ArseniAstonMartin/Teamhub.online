package com.teamhub.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates {@link TenantContext} for every HTTP request so that {@link
 * TenantAwareDataSource} can propagate the tenant identifier to the database session variable
 * {@code app.current_tenant_id} (PRD §7.2).
 *
 * <p>// TO-DO (TASK-019): Replace header-based extraction with JWT claims parsing. Once the RS256
 * access token is validated, read the {@code tenant_id} claim and call {@link TenantContext#set}
 * with the parsed UUID. The {@code X-Tenant-Id} header mechanism below is a placeholder that exists
 * only to wire the filter correctly before JWT infrastructure lands.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

  /** Placeholder header used until JWT extraction is implemented in TASK-019. */
  static final String PLACEHOLDER_HEADER = "X-Tenant-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      String headerValue = request.getHeader(PLACEHOLDER_HEADER);
      if (headerValue != null && !headerValue.isBlank()) {
        try {
          TenantContext.set(UUID.fromString(headerValue.strip()));
        } catch (IllegalArgumentException e) {
          log.warn("Ignoring malformed tenant ID in {} header", PLACEHOLDER_HEADER);
        }
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
