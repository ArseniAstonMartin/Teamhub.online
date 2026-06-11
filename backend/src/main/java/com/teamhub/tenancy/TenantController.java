package com.teamhub.tenancy;

import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.dto.TenantResponse;
import com.teamhub.tenancy.dto.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant profile endpoints (PRD §5.2; TASK-128).
 *
 * <ul>
 *   <li>{@code GET /api/v1/tenant} — Admin-only read of id, name, plan, timezone.
 *   <li>{@code PATCH /api/v1/tenant} — Admin-only partial update of display name (audited).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "tenancy", description = "Tenant profile — PRD §5.2")
public class TenantController {

  private final TenantService tenantService;

  public TenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_tenant.read')")
  @Operation(
      summary = "Get tenant profile",
      description = "Admin only. Returns id, name, plan, timezone.")
  public TenantResponse getTenant() {
    return tenantService.getTenant(TenantContext.get());
  }

  @PatchMapping
  @PreAuthorize("hasAuthority('PERM_tenant.edit')")
  @Operation(
      summary = "Update tenant profile",
      description = "Admin only. Updates display name; audited.")
  public TenantResponse updateTenant(
      @Valid @RequestBody UpdateTenantRequest request, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);
    return tenantService.updateName(tenantId, request.name(), actorUserId);
  }

  private static UUID extractUserId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
      return UUID.fromString(jwtToken.getToken().getSubject());
    }
    return null;
  }
}
