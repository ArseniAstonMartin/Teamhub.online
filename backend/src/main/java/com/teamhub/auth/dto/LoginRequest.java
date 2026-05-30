package com.teamhub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credentials submitted to {@code POST /api/v1/auth/login}. */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
