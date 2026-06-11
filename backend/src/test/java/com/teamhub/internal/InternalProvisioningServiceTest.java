package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.auth.Invite;
import com.teamhub.auth.InviteRepository;
import com.teamhub.auth.InviteService;
import com.teamhub.auth.UserRole;
import com.teamhub.auth.dto.InviteResponse;
import com.teamhub.internal.dto.ProvisionTenantRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalProvisioningServiceTest {

  @Mock private InternalTenantService internalTenantService;

  @Mock private InviteService inviteService;

  @Mock private InviteRepository inviteRepository;

  @InjectMocks private InternalProvisioningService provisioningService;

  @Test
  void provision_createsTenantAndAdminInvite() {
    UUID staffId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID inviteId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);

    when(internalTenantService.provision("Acme", "owner@acme.com", "plumber")).thenReturn(tenant);
    when(inviteService.createInvite(tenantId, staffId, "owner@acme.com", UserRole.ADMIN.name()))
        .thenReturn(
            new InviteResponse(
                inviteId,
                "owner@acme.com",
                "ADMIN",
                Instant.now().plusSeconds(3600),
                Instant.now()));

    var response =
        provisioningService.provision(
            new ProvisionTenantRequest("Acme", "owner@acme.com", "plumber"), staffId);

    assertThat(response.tenantId()).isEqualTo(tenantId);
    assertThat(response.inviteId()).isEqualTo(inviteId);
    assertThat(response.ownerEmail()).isEqualTo("owner@acme.com");
  }

  @Test
  void resendOwnerInvite_delegatesToInviteService() {
    UUID tenantId = UUID.randomUUID();
    UUID inviteId = UUID.randomUUID();
    Invite invite =
        new Invite(tenantId, "owner@acme.com", "ADMIN", "hash", Instant.now().plusSeconds(3600));
    invite.setId(inviteId);

    when(internalTenantService.tenantExists(tenantId)).thenReturn(true);
    when(inviteRepository.findFirstByTenantIdAndRoleAndAcceptedAtIsNullOrderByCreatedAtDesc(
            tenantId, UserRole.ADMIN.name()))
        .thenReturn(Optional.of(invite));

    provisioningService.resendOwnerInvite(tenantId);

    verify(inviteService).resendInvite(tenantId, inviteId);
  }

  @Test
  void resendOwnerInvite_unknownTenant_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(internalTenantService.tenantExists(tenantId)).thenReturn(false);

    assertThatThrownBy(() -> provisioningService.resendOwnerInvite(tenantId))
        .isInstanceOf(InternalTenantNotFoundException.class);
  }

  @Test
  void resendOwnerInvite_noPendingInvite_throwsDomainException() {
    UUID tenantId = UUID.randomUUID();
    when(internalTenantService.tenantExists(tenantId)).thenReturn(true);
    when(inviteRepository.findFirstByTenantIdAndRoleAndAcceptedAtIsNullOrderByCreatedAtDesc(
            eq(tenantId), eq(UserRole.ADMIN.name())))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> provisioningService.resendOwnerInvite(tenantId))
        .isInstanceOf(InternalNoPendingOwnerInviteException.class);
  }
}
