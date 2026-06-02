package com.teamhub.auth;

import com.teamhub.auth.dto.ForgotPasswordRequest;
import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.auth.dto.LogoutRequest;
import com.teamhub.auth.dto.RefreshRequest;
import com.teamhub.auth.dto.ResetPasswordRequest;
import com.teamhub.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints (PRD §6.2, §6.3). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "Authentication — login, refresh, logout, password reset")
public class AuthController {

  private final AuthService authService;

  private final PasswordResetService passwordResetService;

  public AuthController(AuthService authService, PasswordResetService passwordResetService) {
    this.authService = authService;
    this.passwordResetService = passwordResetService;
  }

  /**
   * Authenticates with email + password and issues a token pair.
   *
   * <p>The source IP is extracted from {@code HttpServletRequest} and forwarded to {@link
   * AuthService#login(String, String, String)} so it can be captured in the {@code
   * auth.login_failed} audit row per PRD §19.2.
   */
  @PostMapping("/login")
  public TokenResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    String sourceIp = httpRequest.getRemoteAddr();
    return TokenResponse.from(authService.login(request.email(), request.password(), sourceIp));
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return TokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  /**
   * Initiates a password-reset flow for the given email address (PRD §6.3).
   *
   * <p>Always returns 204 — the response is the same whether or not the email is registered so that
   * no information about existing accounts is leaked.
   */
  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.requestReset(request.email());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  /**
   * Completes a password-reset using the token from the reset email (PRD §6.3).
   *
   * <p>Returns 204 on success; 400 if the token is invalid/used/expired; 422 if validation fails.
   */
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
