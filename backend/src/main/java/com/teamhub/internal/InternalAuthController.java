package com.teamhub.internal;

import com.teamhub.internal.dto.InternalLoginRequest;
import com.teamhub.internal.dto.StaffTokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff authentication for the internal console (PRD §6.4, TASK-131). */
@RestController
@RequestMapping("/api/v1/internal/auth")
public class InternalAuthController {

  private final StaffAuthService staffAuthService;

  public InternalAuthController(StaffAuthService staffAuthService) {
    this.staffAuthService = staffAuthService;
  }

  /**
   * Authenticates a Teamhub staff account and returns an RS256 JWT with {@code role=TEAMHUB_STAFF}
   * and no {@code tenant_id} claim. Permit-all but IP-filtered.
   */
  @PostMapping("/login")
  public StaffTokenResponse login(@Valid @RequestBody InternalLoginRequest request) {
    return StaffTokenResponse.from(staffAuthService.login(request.email(), request.password()));
  }
}
