package com.teamhub.tenancy.dto;

import com.teamhub.tenancy.Plan;
import java.util.UUID;

/** Read-only tenant profile returned by GET /api/v1/tenant (PRD §5.2; TASK-128). */
public record TenantResponse(UUID id, String name, Plan plan, String timezone) {}
