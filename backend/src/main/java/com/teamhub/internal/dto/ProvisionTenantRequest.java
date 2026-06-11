package com.teamhub.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/internal/tenants}. */
public record ProvisionTenantRequest(
    @NotBlank String name, @NotBlank @Email String ownerEmail, String presetKey) {}
