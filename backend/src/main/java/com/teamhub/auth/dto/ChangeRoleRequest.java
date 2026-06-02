package com.teamhub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for PATCH /api/v1/users/{id}/role (TASK-055). */
public record ChangeRoleRequest(@NotBlank String role) {}
