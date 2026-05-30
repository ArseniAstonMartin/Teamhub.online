package com.teamhub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/logout}. */
public record LogoutRequest(@NotBlank String refreshToken) {}
