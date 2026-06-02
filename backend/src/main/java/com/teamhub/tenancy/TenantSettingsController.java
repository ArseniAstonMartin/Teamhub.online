package com.teamhub.tenancy;

import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.dto.TenantSettingsResponse;
import com.teamhub.tenancy.dto.UpdateTenantSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Tenant-level feature-settings endpoints (PRD §5.2, §9.3, §13; TASK-053).
 *
 * <ul>
 *   <li>{@code GET /api/v1/tenant/settings} — readable by ADMIN, DISPATCHER, TECHNICIAN ({@code
 *       PERM_tenant.settings.read}).
 *   <li>{@code PATCH /api/v1/tenant/settings} — Admin-only write gate ({@code
 *       PERM_tenant.settings.edit}).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tenant/settings")
@Tag(name = "tenancy", description = "Tenant settings — PRD §5.2, §9.3, §13")
public class TenantSettingsController {

  private final TenantSettingsService tenantSettingsService;

  public TenantSettingsController(TenantSettingsService tenantSettingsService) {
    this.tenantSettingsService = tenantSettingsService;
  }

  /**
   * Returns the current tenant feature settings.
   *
   * <p>All authenticated roles (ADMIN, DISPATCHER, TECHNICIAN) may call this endpoint. Mobile
   * clients fetch on app start and after receiving a {@code tenant.settings.changed} push (PRD
   * §15.2) to decide whether to show the Complete button.
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_tenant.settings.read')")
  @Operation(
      summary = "Get tenant settings",
      description = "Returns the current feature settings for the caller's tenant.")
  public TenantSettingsResponse getSettings() {
    UUID tenantId = TenantContext.get();
    return tenantSettingsService.getSettings(tenantId);
  }

  /**
   * Partially updates tenant feature settings. Admin only.
   *
   * <p>Only non-null fields in the request body are applied, keeping the endpoint
   * forward-compatible when new toggles are added.
   */
  @PatchMapping
  @PreAuthorize("hasAuthority('PERM_tenant.settings.edit')")
  @Operation(
      summary = "Update tenant settings",
      description = "Admin only. Partial update: only non-null fields are applied.")
  public TenantSettingsResponse updateSettings(
      @RequestBody UpdateTenantSettingsRequest request, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    UUID actorUserId = extractUserId(authentication);

    TenantSettingsResponse result = tenantSettingsService.getSettings(tenantId);

    if (request.closeJobsEnabled() != null) {
      result =
          tenantSettingsService.setCloseJobsEnabled(
              tenantId, request.closeJobsEnabled(), actorUserId);
    }

    return result;
  }

  private static UUID extractUserId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
      return UUID.fromString(jwtToken.getToken().getSubject());
    }
    return null;
  }
}
