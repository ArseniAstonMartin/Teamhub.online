package com.teamhub.push.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/devices/unregister} (PRD §15.1). */
public record UnregisterDeviceRequest(@NotBlank String expoToken) {}
