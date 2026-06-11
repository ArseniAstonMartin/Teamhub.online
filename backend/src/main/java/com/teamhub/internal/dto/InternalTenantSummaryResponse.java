package com.teamhub.internal.dto;

import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.util.UUID;

/** List item for {@code GET /api/v1/internal/tenants}. */
public record InternalTenantSummaryResponse(
    UUID id, String name, Plan plan, Instant createdAt, long userCount) {

  public static InternalTenantSummaryResponse of(Tenant tenant, long userCount) {
    return new InternalTenantSummaryResponse(
        tenant.getId(), tenant.getName(), tenant.getPlan(), tenant.getCreatedAt(), userCount);
  }
}
