package com.teamhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for the forgot-password endpoint (TASK-020). */
public record ForgotPasswordRequest(@Email @NotBlank String email) {}
