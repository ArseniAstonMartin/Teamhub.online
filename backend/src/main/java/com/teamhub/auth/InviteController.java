package com.teamhub.auth;

import com.teamhub.auth.dto.AcceptInviteRequest;
import com.teamhub.auth.dto.CreateInviteRequest;
import com.teamhub.auth.dto.InviteResponse;
import com.teamhub.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant invite endpoints (PRD §6.5, TASK-054). */
@RestController
@RequestMapping("/api/v1/invites")
@Tag(name = "auth")
public class InviteController {

  private final InviteService inviteService;

  public InviteController(InviteService inviteService) {
    this.inviteService = inviteService;
  }

  /**
   * Creates an invite for the given email and role within the caller's tenant.
   *
   * <p>Requires {@code PERM_invite.create} (Admin only). Returns 201 with the persisted invite
   * details.
   */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_invite.create')")
  public ResponseEntity<InviteResponse> createInvite(
      @Valid @RequestBody CreateInviteRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    UUID actorUserId = UUID.fromString(jwt.getSubject());
    InviteResponse response =
        inviteService.createInvite(tenantId, actorUserId, request.email(), request.role());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Accepts an invite token and creates the user account. Public endpoint — no authentication
   * required. Returns 200 with a live access + refresh token pair so the invitee is logged in
   * immediately.
   */
  @PostMapping("/accept")
  public TokenResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
    return inviteService.acceptInvite(request.token(), request.password());
  }

  /**
   * Resends the invite email with a fresh token. Requires {@code PERM_invite.manage} (Admin only).
   * Rate-limited to one resend per 5 minutes per invite.
   */
  @PostMapping("/{id}/resend")
  @PreAuthorize("hasAuthority('PERM_invite.manage')")
  public ResponseEntity<Void> resendInvite(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    inviteService.resendInvite(tenantId, id);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
