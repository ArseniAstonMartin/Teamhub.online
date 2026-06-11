package com.teamhub.internal.dto;

import java.util.UUID;

/** Response from {@code POST /api/v1/internal/tenants}. */
public record ProvisionTenantResponse(UUID tenantId, UUID inviteId, String ownerEmail) {}
