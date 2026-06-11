package com.teamhub.internal;

import com.teamhub.auth.Invite;
import com.teamhub.auth.InviteRepository;
import com.teamhub.auth.InviteService;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.dto.InviteResponse;
import com.teamhub.internal.dto.ProvisionTenantRequest;
import com.teamhub.internal.dto.ProvisionTenantResponse;
import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates tenant provisioning + owner invite for the internal console (PRD §6.4, TASK-131).
 *
 * <p>Provisioning is invite-only: no tenant user row is created until the owner accepts the invite.
 */
@Service
public class InternalProvisioningService {

  private final InternalTenantService internalTenantService;

  private final InviteService inviteService;

  private final InviteRepository inviteRepository;

  public InternalProvisioningService(
      InternalTenantService internalTenantService,
      InviteService inviteService,
      InviteRepository inviteRepository) {
    this.internalTenantService = internalTenantService;
    this.inviteService = inviteService;
    this.inviteRepository = inviteRepository;
  }

  /**
   * Provisions a tenant, creates an ADMIN invite for the owner, and sends the invite email.
   *
   * @param request provision parameters
   * @param staffUserId authenticated staff user id (audit actor for invite event)
   */
  @Transactional
  public ProvisionTenantResponse provision(ProvisionTenantRequest request, UUID staffUserId) {
    Tenant tenant =
        internalTenantService.provision(request.name(), request.ownerEmail(), request.presetKey());

    InviteResponse invite;
    try {
      TenantContext.set(tenant.getId());
      invite =
          inviteService.createInvite(
              tenant.getId(), staffUserId, request.ownerEmail(), UserRole.ADMIN.name());
    } finally {
      TenantContext.clear();
    }

    return new ProvisionTenantResponse(tenant.getId(), invite.id(), request.ownerEmail());
  }

  /** Resends the pending owner (ADMIN) invite with a 5-minute cooldown. */
  @Transactional
  public void resendOwnerInvite(UUID tenantId) {
    if (!internalTenantService.tenantExists(tenantId)) {
      throw new InternalTenantNotFoundException(tenantId);
    }

    Invite invite =
        inviteRepository
            .findFirstByTenantIdAndRoleAndAcceptedAtIsNullOrderByCreatedAtDesc(
                tenantId, UserRole.ADMIN.name())
            .orElseThrow(InternalNoPendingOwnerInviteException::new);

    inviteService.resendInvite(tenantId, invite.getId());
  }
}
