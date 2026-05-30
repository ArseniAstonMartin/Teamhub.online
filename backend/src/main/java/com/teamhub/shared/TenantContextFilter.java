package com.teamhub.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates {@link TenantContext} from the validated JWT so that {@link
 * TenantAwareDataSource} can propagate the tenant identifier to the database session variable
 * {@code app.current_tenant_id} (PRD §7.2).
 *
 * <p>Registered inside the Spring Security filter chain <em>after</em> JWT authentication (see
 * {@code SecurityConfig}), so the {@code tenant_id} claim of the authenticated token is available.
 * Requests without a JWT (the public auth endpoints) leave the context unset, in which case {@link
 * TenantAwareDataSource} resets the role and GUC — the login DB user then performs the global email
 * / refresh-token lookups those flows require.
 */
public class TenantContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

  /** JWT claim carrying the tenant identifier (set by {@code JwtService}). */
  static final String TENANT_ID_CLAIM = "tenant_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      resolveTenantId().ifPresent(TenantContext::set);
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private Optional<UUID> resolveTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      return Optional.empty();
    }
    Jwt jwt = jwtAuth.getToken();
    String claim = jwt.getClaimAsString(TENANT_ID_CLAIM);
    if (claim == null || claim.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(claim));
    } catch (IllegalArgumentException e) {
      log.warn("Ignoring malformed {} claim in JWT", TENANT_ID_CLAIM);
      return Optional.empty();
    }
  }
}
