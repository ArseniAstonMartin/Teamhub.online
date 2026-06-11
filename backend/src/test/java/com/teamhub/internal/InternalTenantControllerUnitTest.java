package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.internal.dto.InternalTenantDetailResponse;
import com.teamhub.internal.dto.ProvisionTenantRequest;
import com.teamhub.internal.dto.ProvisionTenantResponse;
import com.teamhub.internal.dto.SchemaPresetItemResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalTenantControllerUnitTest {

  @Mock private InternalTenantQueryService queryService;

  @Mock private InternalProvisioningService provisioningService;

  @InjectMocks private InternalTenantController controller;

  @Test
  void listSchemaPresets_delegatesToQueryService() {
    when(queryService.listSchemaPresets())
        .thenReturn(List.of(new SchemaPresetItemResponse("plumber", "Plumber")));

    assertThat(controller.listSchemaPresets()).hasSize(1);
  }

  @Test
  void provisionTenant_passesStaffSubjectToService() {
    UUID staffId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(staffId.toString())
            .claim("role", StaffJwtService.STAFF_ROLE_CLAIM)
            .build();
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

    when(provisioningService.provision(
            new ProvisionTenantRequest("Acme", "owner@acme.com", "plumber"), staffId))
        .thenReturn(new ProvisionTenantResponse(tenantId, UUID.randomUUID(), "owner@acme.com"));

    ProvisionTenantResponse response =
        controller.provisionTenant(
            new ProvisionTenantRequest("Acme", "owner@acme.com", "plumber"), auth);

    assertThat(response.tenantId()).isEqualTo(tenantId);
  }

  @Test
  void getTenant_delegatesToQueryService() {
    UUID tenantId = UUID.randomUUID();
    InternalTenantDetailResponse expected =
        InternalTenantDetailResponse.of(new com.teamhub.tenancy.Tenant("Acme"), 1L, 2L, "Plumber");
    when(queryService.getTenantDetail(tenantId)).thenReturn(expected);

    assertThat(controller.getTenant(tenantId)).isEqualTo(expected);
  }

  @Test
  void resendOwnerInvite_returns204() {
    UUID tenantId = UUID.randomUUID();

    ResponseEntity<Void> response = controller.resendOwnerInvite(tenantId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(provisioningService).resendOwnerInvite(tenantId);
  }

  @Test
  void listTenants_delegatesToQueryService() {
    when(queryService.listTenants(PageRequest.of(0, 20)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    com.teamhub.internal.dto.InternalTenantSummaryResponse.of(
                        new com.teamhub.tenancy.Tenant("Acme"), 0L))));

    assertThat(controller.listTenants(PageRequest.of(0, 20)).getContent()).hasSize(1);
  }
}
