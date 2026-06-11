package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.teamhub.auth.UserRepository;
import com.teamhub.internal.dto.InternalTenantDetailResponse;
import com.teamhub.job.JobRepository;
import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.AppointmentSchemaRepository;
import com.teamhub.schemas.SchemaPreset;
import com.teamhub.schemas.SchemaPresetRepository;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalTenantQueryServiceTest {

  @Mock private SchemaPresetRepository schemaPresetRepository;

  @Mock private TenantRepository tenantRepository;

  @Mock private UserRepository userRepository;

  @Mock private JobRepository jobRepository;

  @Mock private AppointmentSchemaRepository appointmentSchemaRepository;

  @InjectMocks private InternalTenantQueryService queryService;

  @Test
  void listSchemaPresets_mapsPresetFields() {
    SchemaPreset preset = org.mockito.Mockito.mock(SchemaPreset.class);
    when(preset.getPresetKey()).thenReturn("plumber");
    when(preset.getSchemaName()).thenReturn("Plumber");
    when(schemaPresetRepository.findAll()).thenReturn(List.of(preset));

    var result = queryService.listSchemaPresets();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).presetKey()).isEqualTo("plumber");
    assertThat(result.get(0).schemaName()).isEqualTo("Plumber");
  }

  @Test
  void getTenantDetail_returnsAggregatedCounts() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    tenant.setPlan(Plan.FREE);
    tenant.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    tenant.setCurrentPeriodEnd(Instant.parse("2026-02-01T00:00:00Z"));

    AppointmentSchema schema = org.mockito.Mockito.mock(AppointmentSchema.class);
    when(schema.getName()).thenReturn("Plumber");

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId)).thenReturn(3L);
    when(jobRepository.countByTenantId(tenantId)).thenReturn(12L);
    when(appointmentSchemaRepository.findByTenantIdAndActiveTrue(tenantId))
        .thenReturn(List.of(schema));

    InternalTenantDetailResponse detail = queryService.getTenantDetail(tenantId);

    assertThat(detail.userCount()).isEqualTo(3L);
    assertThat(detail.jobCount()).isEqualTo(12L);
    assertThat(detail.activeSchemaName()).isEqualTo("Plumber");
    assertThat(detail.currentPeriodEnd()).isEqualTo(tenant.getCurrentPeriodEnd());
  }

  @Test
  void getTenantDetail_missingTenant_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> queryService.getTenantDetail(tenantId))
        .isInstanceOf(InternalTenantNotFoundException.class);
  }

  @Test
  void listTenants_includesUserCount() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    when(tenantRepository.findAll(PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(tenant)));
    when(userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId)).thenReturn(2L);

    var page = queryService.listTenants(PageRequest.of(0, 20));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).userCount()).isEqualTo(2L);
  }
}
