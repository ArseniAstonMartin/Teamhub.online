package com.teamhub.tenancy.dto;

import jakarta.validation.constraints.NotBlank;

/** Partial tenant profile update — currently supports display name only (TASK-128). */
public record UpdateTenantRequest(@NotBlank String name) {}
