package com.teamhub.auth;

import com.teamhub.auth.dto.LoginRequest;
import com.teamhub.auth.dto.LogoutRequest;
import com.teamhub.auth.dto.RefreshRequest;
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

/** Public authentication endpoints (PRD §6.2). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "Authentication — login, refresh, logout")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
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
}
