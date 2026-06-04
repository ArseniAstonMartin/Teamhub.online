package com.teamhub.push.dto;

import com.teamhub.push.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /api/v1/devices/register} (PRD §15.1). */
public record RegisterDeviceRequest(@NotBlank String expoToken, @NotNull DevicePlatform platform) {}
