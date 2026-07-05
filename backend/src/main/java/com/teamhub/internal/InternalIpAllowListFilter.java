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
 * discoverable. Client IP is taken from {@code X-Forwarded-For} (first hop) when present, for
 * requests behind a reverse proxy (Coolify/Traefik).
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
    String clientIp = resolveClientIp(request);
    if (!properties.allowedIpSet().contains(clientIp)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }

  /**
   * Resolves the client IP from {@code X-Forwarded-For} (first entry) or falls back to {@link
   * HttpServletRequest#getRemoteAddr()}.
   */
  static String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).strip();
    }
    return request.getRemoteAddr();
  }
}
