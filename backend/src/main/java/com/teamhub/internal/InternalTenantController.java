package com.teamhub.internal;

import com.teamhub.internal.dto.InternalTenantDetailResponse;
import com.teamhub.internal.dto.InternalTenantSummaryResponse;
import com.teamhub.internal.dto.ProvisionTenantRequest;
import com.teamhub.internal.dto.ProvisionTenantResponse;
import com.teamhub.internal.dto.SchemaPresetItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal tenant provisioning and listing API (PRD §6.4, TASK-131).
 *
 * <p>TO-DO: {@code POST /api/v1/internal/staff/invites} for root staff inviting additional
 * TEAMHUB_STAFF accounts — follow-up task, not in TASK-131 scope.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalTenantController {

  private final InternalTenantQueryService queryService;

  private final InternalProvisioningService provisioningService;

  public InternalTenantController(
      InternalTenantQueryService queryService, InternalProvisioningService provisioningService) {
    this.queryService = queryService;
    this.provisioningService = provisioningService;
  }

  @GetMapping("/schema-presets")
  public List<SchemaPresetItemResponse> listSchemaPresets() {
    return queryService.listSchemaPresets();
  }

  @PostMapping("/tenants")
  public ProvisionTenantResponse provisionTenant(
      @Valid @RequestBody ProvisionTenantRequest request, JwtAuthenticationToken auth) {
    UUID staffUserId = UUID.fromString(auth.getToken().getSubject());
    return provisioningService.provision(request, staffUserId);
  }

  @GetMapping("/tenants")
  public Page<InternalTenantSummaryResponse> listTenants(
      @PageableDefault(size = 20) Pageable pageable) {
    return queryService.listTenants(pageable);
  }

  @GetMapping("/tenants/{id}")
  public InternalTenantDetailResponse getTenant(@PathVariable UUID id) {
    return queryService.getTenantDetail(id);
  }

  @PostMapping("/tenants/{id}/resend-invite")
  public ResponseEntity<Void> resendOwnerInvite(@PathVariable UUID id) {
    provisioningService.resendOwnerInvite(id);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
