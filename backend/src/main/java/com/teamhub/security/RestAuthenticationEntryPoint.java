package com.teamhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Emits {@code 401 Unauthorized} using the uniform error envelope (PRD §23.4) whenever an anonymous
 * request reaches a protected route, or when a presented token is invalid / expired.
 *
 * <p>Wired via {@code SecurityConfig.exceptionHandling}, this replaces Spring Security's default
 * {@code WWW-Authenticate: Bearer} challenge response with a machine-readable JSON body so clients
 * can display a consistent error message (PRD §5.5, §23.4, TASK-024).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  /** Documented machine-readable error code for unauthenticated access (PRD §23.4). */
  public static final String CODE = "UNAUTHORIZED";

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ErrorEnvelope body =
        ErrorEnvelope.of(CODE, "Authentication is required to access this resource.");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
