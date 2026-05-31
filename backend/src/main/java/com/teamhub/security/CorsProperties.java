package com.teamhub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration (PRD §5.5, TASK-024).
 *
 * <p>The allowed SPA origin defaults to {@code http://localhost:5173} (Vite dev server) and is
 * overridden in production by the {@code CORS_ALLOWED_ORIGIN} environment variable set to {@code
 * https://app.teamhub.online}.
 *
 * @param allowedOrigin the single allow-listed SPA origin
 */
@ConfigurationProperties(prefix = "teamhub.cors")
public record CorsProperties(String allowedOrigin) {

  public CorsProperties {
    if (allowedOrigin == null || allowedOrigin.isBlank()) {
      allowedOrigin = "http://localhost:5173";
    }
  }
}
