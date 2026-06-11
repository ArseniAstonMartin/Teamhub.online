package com.teamhub.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects tenant JWTs on internal routes — only {@code TEAMHUB_STAFF} is accepted (TASK-131).
 *
 * <p>Runs after JWT validation on the internal security chain. Unauthenticated requests (login
 * endpoint) are passed through.
 */
public class InternalStaffRoleFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String role = jwt.getClaimAsString("role");
      if (!StaffJwtService.STAFF_ROLE_CLAIM.equals(role)) {
        response.sendError(HttpStatus.UNAUTHORIZED.value());
        return;
      }
    }

    chain.doFilter(request, response);
  }
}
