package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.tenancy.dto.TenantResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link TenantService} (TASK-128). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TenantServiceUnitTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private AsyncAuditService asyncAuditService;

  private TenantService service() {
    return new TenantService(tenantRepository, asyncAuditService);
  }

  @Test
  void getTenant_existingTenant_returnsResponse() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme Plumbing");
    tenant.setId(tenantId);
    tenant.setTimezone("America/Chicago");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    TenantResponse response = service().getTenant(tenantId);

    assertThat(response.name()).isEqualTo("Acme Plumbing");
    assertThat(response.timezone()).isEqualTo("America/Chicago");
  }

  @Test
  void getTenant_missingTenant_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().getTenant(tenantId))
        .isInstanceOf(TenantSettingsService.TenantNotFoundException.class);
  }

  @Test
  void updateName_persistsAndAudits() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Tenant tenant = new Tenant("Old Name");
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantResponse response = service().updateName(tenantId, "New Name", actorId);

    assertThat(response.name()).isEqualTo("New Name");
    verify(asyncAuditService).recordAsync(any());
    ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(saved.capture());
    assertThat(saved.getValue().getName()).isEqualTo("New Name");
  }
}
