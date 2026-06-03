package com.teamhub.integration.geocoding;

/**
 * Geocoding result returned by {@link GeocodingService#geocode(String)} and the {@code POST
 * /api/v1/geocode} endpoint (PRD §8.1, TASK-038).
 *
 * @param city locality component (e.g. "Mountain View")
 * @param state administrative_area_level_1 short name (e.g. "CA")
 * @param zip postal_code component (e.g. "94043")
 * @param lat WGS-84 latitude
 * @param lon WGS-84 longitude
 */
public record GeocodeResult(String city, String state, String zip, double lat, double lon) {}
