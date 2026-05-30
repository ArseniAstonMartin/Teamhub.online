package com.teamhub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/refresh}. */
public record RefreshRequest(@NotBlank String refreshToken) {}
