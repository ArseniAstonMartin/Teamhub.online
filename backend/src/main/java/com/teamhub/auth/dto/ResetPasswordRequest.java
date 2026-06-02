package com.teamhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for the reset-password endpoint (TASK-020). */
public record ResetPasswordRequest(
    @NotBlank String token, @Size(min = 8) @NotBlank String newPassword) {}
