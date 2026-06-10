package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.audit.AsyncAuditService;
import com.teamhub.tenancy.dto.TenantSettingsResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link TenantSettingsService} (TASK-128). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TenantSettingsServiceUnitTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private AsyncAuditService asyncAuditService;

  private TenantSettingsService service() {
    return new TenantSettingsService(tenantRepository, asyncAuditService);
  }

  @Test
  void getSettings_returnsCurrentValues() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    tenant.setCloseJobsEnabled(true);
    tenant.setTimezone("America/Denver");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    TenantSettingsResponse response = service().getSettings(tenantId);

    assertThat(response.closeJobsEnabled()).isTrue();
    assertThat(response.timezone()).isEqualTo("America/Denver");
  }

  @Test
  void setCloseJobsEnabled_persistsAndAudits() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantSettingsResponse response = service().setCloseJobsEnabled(tenantId, true, actorId);

    assertThat(response.closeJobsEnabled()).isTrue();
    verify(asyncAuditService).recordAsync(any());
  }

  @Test
  void setTimezone_allowedZone_persistsAndAudits() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantSettingsResponse response =
        service().setTimezone(tenantId, "America/Los_Angeles", actorId);

    assertThat(response.timezone()).isEqualTo("America/Los_Angeles");
    verify(asyncAuditService).recordAsync(any());
  }

  @Test
  void setTimezone_invalidZone_throws() {
    UUID tenantId = UUID.randomUUID();

    assertThatThrownBy(() -> service().setTimezone(tenantId, "Europe/London", UUID.randomUUID()))
        .isInstanceOf(InvalidTenantTimezoneException.class);
  }
}
