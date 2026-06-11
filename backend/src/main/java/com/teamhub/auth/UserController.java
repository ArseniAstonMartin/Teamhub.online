package com.teamhub.auth;

import com.teamhub.auth.dto.ChangeRoleRequest;
import com.teamhub.auth.dto.CurrentUserResponse;
import com.teamhub.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-management endpoints for Admins (PRD §5.2, TASK-055). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users")
public class UserController {

  private final UserManagementService userManagementService;

  private final CurrentUserService currentUserService;

  public UserController(
      UserManagementService userManagementService, CurrentUserService currentUserService) {
    this.userManagementService = userManagementService;
    this.currentUserService = currentUserService;
  }

  /**
   * Returns the authenticated caller's profile. Any valid tenant JWT (TECHNICIAN, DISPATCHER, or
   * ADMIN) is accepted (TASK-132, PRD §6.2).
   */
  @GetMapping("/me")
  public CurrentUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    UUID userId = UUID.fromString(jwt.getSubject());
    return currentUserService.getCurrentUser(tenantId, userId);
  }

  /**
   * Lists all users in the caller's tenant (paginated). Requires {@code PERM_user.list} (Admin
   * only).
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_user.list')")
  public Page<UserResponse> listUsers(
      @AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 20) Pageable pageable) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    return userManagementService.listUsers(tenantId, pageable);
  }

  /**
   * Deactivates a user within the caller's tenant. Requires {@code PERM_user.deactivate} (Admin
   * only). Returns 204 No Content.
   */
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasAuthority('PERM_user.deactivate')")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    userManagementService.deactivate(tenantId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Reactivates a previously deactivated user. Requires {@code PERM_user.reactivate} (Admin only).
   * Returns 204 No Content.
   */
  @PostMapping("/{id}/reactivate")
  @PreAuthorize("hasAuthority('PERM_user.reactivate')")
  public ResponseEntity<Void> reactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    userManagementService.reactivate(tenantId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Changes a user's role within the caller's tenant. Requires {@code PERM_user.role.change} (Admin
   * only). Returns 204 No Content.
   */
  @PatchMapping("/{id}/role")
  @PreAuthorize("hasAuthority('PERM_user.role.change')")
  public ResponseEntity<Void> changeRole(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRoleRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    userManagementService.changeRole(tenantId, id, request.role());
    return ResponseEntity.noContent().build();
  }
}
