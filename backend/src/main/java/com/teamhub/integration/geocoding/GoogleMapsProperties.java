package com.teamhub.integration.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Maps API configuration (PRD §8.1, TASK-038).
 *
 * <p>The {@code mapsApiKey} is sourced from the {@code GOOGLE_MAPS_API_KEY} environment variable.
 * When the variable is absent or blank (local dev without a key) geocoding calls throw {@link
 * GeocodingFailedException} with a descriptive message rather than making a bad API call.
 *
 * @param mapsApiKey Google Maps / Geocoding API key (server-side use only)
 */
@ConfigurationProperties(prefix = "teamhub.google")
public record GoogleMapsProperties(String mapsApiKey) {}
