package com.teamhub.integration.geocoding;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/geocode} (PRD §8.1, TASK-038).
 *
 * @param address free-text address to geocode (required)
 */
public record GeocodeRequest(@NotBlank String address) {}
