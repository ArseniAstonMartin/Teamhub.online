package com.teamhub.internal.dto;

import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.UUID;

/** Detail payload for {@code GET /api/v1/internal/tenants/{id}}. */
public record InternalTenantDetailResponse(
    UUID id,
    String name,
    Plan plan,
    Instant createdAt,
    long userCount,
    long jobCount,
    Instant currentPeriodEnd,
    String activeSchemaName) {

  public static InternalTenantDetailResponse of(
      Tenant tenant, long userCount, long jobCount, String activeSchemaName) {
    return new InternalTenantDetailResponse(
        tenant.getId(),
        tenant.getName(),
        tenant.getPlan(),
        tenant.getCreatedAt(),
        userCount,
        jobCount,
        tenant.getCurrentPeriodEnd(),
        activeSchemaName);
  }
}
