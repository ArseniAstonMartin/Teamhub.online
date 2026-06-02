package com.teamhub.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Handles a failed Google OAuth2 login by redirecting to the SPA error page (PRD §6.1, TASK-021).
 *
 * <p>Appends {@code error=OAUTH2_ERROR} to the failure redirect URL so the frontend can display an
 * appropriate message to the user.
 */
@Component
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

  private final String failureRedirectUrl;

  public GoogleOAuth2FailureHandler(
      @Value("${teamhub.oauth2.failure-redirect-url}") String failureRedirectUrl) {
    this.failureRedirectUrl = failureRedirectUrl;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.sendRedirect(failureRedirectUrl + "?error=OAUTH2_ERROR");
  }
}
