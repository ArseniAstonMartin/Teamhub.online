package com.teamhub.tenancy;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import com.teamhub.exception.DomainException;
import com.teamhub.tenancy.dto.TenantResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates tenant profile fields (display name) for GET/PATCH /api/v1/tenant (TASK-128).
 */
@Service
public class TenantService {

  private final TenantRepository tenantRepository;

  private final AsyncAuditService asyncAuditService;

  public TenantService(TenantRepository tenantRepository, AsyncAuditService asyncAuditService) {
    this.tenantRepository = tenantRepository;
    this.asyncAuditService = asyncAuditService;
  }

  @Transactional(readOnly = true)
  public TenantResponse getTenant(UUID tenantId) {
    return toResponse(loadTenant(tenantId));
  }

  @Transactional
  public TenantResponse updateName(UUID tenantId, String name, UUID actorUserId) {
    Tenant tenant = loadTenant(tenantId);
    String previous = tenant.getName();
    tenant.setName(name);
    tenantRepository.save(tenant);

    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(actorUserId)
            .action("tenant.name.updated")
            .resourceType("tenant")
            .resourceId(tenantId)
            .beforeState("{\"name\":\"" + escapeJson(previous) + "\"}")
            .afterState("{\"name\":\"" + escapeJson(name) + "\"}")
            .build());

    return toResponse(tenant);
  }

  private Tenant loadTenant(UUID tenantId) {
    return tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new TenantSettingsService.TenantNotFoundException(tenantId));
  }

  private static TenantResponse toResponse(Tenant tenant) {
    return new TenantResponse(
        tenant.getId(), tenant.getName(), tenant.getPlan(), tenant.getTimezone());
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** Thrown when a tenant ID cannot be resolved (HTTP 404). */
  static class TenantNotFoundException extends DomainException {

    static final String CODE = "TENANT_NOT_FOUND";

    TenantNotFoundException(UUID tenantId) {
      super(CODE, HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
    }
  }
}
