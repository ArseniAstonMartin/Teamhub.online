package com.teamhub.internal;

import com.teamhub.auth.UserRepository;
import com.teamhub.internal.dto.InternalTenantDetailResponse;
import com.teamhub.internal.dto.InternalTenantSummaryResponse;
import com.teamhub.internal.dto.SchemaPresetItemResponse;
import com.teamhub.job.JobRepository;
import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.AppointmentSchemaRepository;
import com.teamhub.schemas.SchemaPresetRepository;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries for the internal tenant console (TASK-131). */
@Service
public class InternalTenantQueryService {

  private final SchemaPresetRepository schemaPresetRepository;

  private final TenantRepository tenantRepository;

  private final UserRepository userRepository;

  private final JobRepository jobRepository;

  private final AppointmentSchemaRepository appointmentSchemaRepository;

  public InternalTenantQueryService(
      SchemaPresetRepository schemaPresetRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      JobRepository jobRepository,
      AppointmentSchemaRepository appointmentSchemaRepository) {
    this.schemaPresetRepository = schemaPresetRepository;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.appointmentSchemaRepository = appointmentSchemaRepository;
  }

  @Transactional(readOnly = true)
  public List<SchemaPresetItemResponse> listSchemaPresets() {
    return schemaPresetRepository.findAll().stream().map(SchemaPresetItemResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public Page<InternalTenantSummaryResponse> listTenants(Pageable pageable) {
    return tenantRepository
        .findAll(pageable)
        .map(
            tenant -> {
              long userCount = userRepository.countByTenantIdAndDeactivatedAtIsNull(tenant.getId());
              return InternalTenantSummaryResponse.of(tenant, userCount);
            });
  }

  @Transactional(readOnly = true)
  public InternalTenantDetailResponse getTenantDetail(UUID tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new InternalTenantNotFoundException(tenantId));

    long userCount = userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId);
    long jobCount = jobRepository.countByTenantId(tenantId);
    String activeSchemaName = resolveActiveSchemaName(tenantId);

    return InternalTenantDetailResponse.of(tenant, userCount, jobCount, activeSchemaName);
  }

  private String resolveActiveSchemaName(UUID tenantId) {
    List<AppointmentSchema> active =
        appointmentSchemaRepository.findByTenantIdAndActiveTrue(tenantId);
    return active.stream()
        .map(AppointmentSchema::getName)
        .min(Comparator.naturalOrder())
        .orElse(null);
  }
}
