package com.teamhub.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP allow-list for {@code /api/v1/internal/**} (PRD §6.4, TASK-131).
 *
 * <p>Non-allow-listed clients receive HTTP 404 (not 403) so the internal surface is not
 * discoverable.
 */
public class InternalIpAllowListFilter extends OncePerRequestFilter {

  private final InternalProperties properties;

  public InternalIpAllowListFilter(InternalProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String clientIp = request.getRemoteAddr();
    if (!properties.allowedIpSet().contains(clientIp)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }
}
