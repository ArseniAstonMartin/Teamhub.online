package com.teamhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Emits {@code 403 Forbidden} using the uniform error envelope (PRD §23.4) whenever an
 * authenticated caller lacks the required permission — for example a {@code @PreAuthorize}
 * permission check on a controller method, or a cross-tenant/cross-role denial (PRD §5.5).
 *
 * <p>Wired via {@code SecurityConfig.exceptionHandling}, this catches {@link AccessDeniedException}
 * thrown by method security and the request-level authorization manager, guaranteeing a 403 with a
 * documented {@code code} rather than a default Spring body or a 404.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  /** Documented machine-readable error code for authorization denials (PRD §5.5, §23.4). */
  public static final String CODE = "FORBIDDEN";

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ErrorEnvelope body =
        ErrorEnvelope.of(CODE, "You do not have permission to perform this action.");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
