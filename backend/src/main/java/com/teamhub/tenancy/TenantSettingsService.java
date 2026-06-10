package com.teamhub.tenancy;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import com.teamhub.exception.DomainException;
import com.teamhub.tenancy.dto.TenantSettingsResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the tenant-level feature settings (PRD §5.2, §9.3, §13; TASK-053).
 *
 * <p>The {@code close_jobs_enabled} toggle controls whether Technicians may transition a job
 * directly to {@code COMPLETED}. The state machine consults this service before allowing that
 * transition (see {@link com.teamhub.job.JobStateMachineService}).
 */
@Service
public class TenantSettingsService {

  private final TenantRepository tenantRepository;

  private final AsyncAuditService asyncAuditService;

  public TenantSettingsService(
      TenantRepository tenantRepository, AsyncAuditService asyncAuditService) {
    this.tenantRepository = tenantRepository;
    this.asyncAuditService = asyncAuditService;
  }

  /**
   * Returns the current feature settings for the given tenant.
   *
   * @param tenantId the tenant whose settings to load
   * @return the settings response DTO
   * @throws DomainException HTTP 404 if the tenant does not exist
   */
  @Transactional(readOnly = true)
  public TenantSettingsResponse getSettings(UUID tenantId) {
    Tenant tenant = loadTenant(tenantId);
    return toResponse(tenant);
  }

  /**
   * Toggles the {@code close_jobs_enabled} flag and emits an async audit event.
   *
   * <p>Audit action: {@code tenant.close_jobs.toggled}; resource type: {@code tenant}; before/after
   * states carry the old and new boolean values.
   *
   * @param tenantId the tenant to update
   * @param enabled the desired value for the toggle
   * @param actorUserId the Admin user performing the change
   * @throws DomainException HTTP 404 if the tenant does not exist
   */
  @Transactional
  public TenantSettingsResponse setCloseJobsEnabled(
      UUID tenantId, boolean enabled, UUID actorUserId) {
    Tenant tenant = loadTenant(tenantId);
    boolean previous = tenant.isCloseJobsEnabled();
    tenant.setCloseJobsEnabled(enabled);
    tenantRepository.save(tenant);

    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(actorUserId)
            .action("tenant.close_jobs.toggled")
            .resourceType("tenant")
            .resourceId(tenantId)
            .beforeState("{\"closeJobsEnabled\":" + previous + "}")
            .afterState("{\"closeJobsEnabled\":" + enabled + "}")
            .build());

    return toResponse(tenant);
  }

  /**
   * Updates the tenant time zone and emits an async audit event.
   *
   * <p>Audit action: {@code tenant.timezone.updated}; resource type: {@code tenant}.
   *
   * @param tenantId the tenant to update
   * @param timezone IANA time zone from the allowed US set
   * @param actorUserId the Admin user performing the change
   * @throws InvalidTenantTimezoneException HTTP 422 when the zone is not allowed
   * @throws DomainException HTTP 404 if the tenant does not exist
   */
  @Transactional
  public TenantSettingsResponse setTimezone(UUID tenantId, String timezone, UUID actorUserId) {
    if (!TenantTimezones.isAllowed(timezone)) {
      throw new InvalidTenantTimezoneException(timezone);
    }

    Tenant tenant = loadTenant(tenantId);
    String previous = tenant.getTimezone();
    tenant.setTimezone(timezone);
    tenantRepository.save(tenant);

    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(actorUserId)
            .action("tenant.timezone.updated")
            .resourceType("tenant")
            .resourceId(tenantId)
            .beforeState("{\"timezone\":\"" + previous + "\"}")
            .afterState("{\"timezone\":\"" + timezone + "\"}")
            .build());

    return toResponse(tenant);
  }

  private static TenantSettingsResponse toResponse(Tenant tenant) {
    return new TenantSettingsResponse(tenant.isCloseJobsEnabled(), tenant.getTimezone());
  }

  private Tenant loadTenant(UUID tenantId) {
    return tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new TenantNotFoundException(tenantId));
  }

  /** Thrown when a tenant ID cannot be resolved (HTTP 404). */
  static class TenantNotFoundException extends DomainException {

    static final String CODE = "TENANT_NOT_FOUND";

    TenantNotFoundException(UUID tenantId) {
      super(CODE, HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
    }
  }
}
