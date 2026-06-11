package com.teamhub.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/internal/auth/login}. */
public record InternalLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
