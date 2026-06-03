package com.teamhub.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/customers} (PRD §11.3).
 *
 * <p>Two modes for supplying location data:
 *
 * <ol>
 *   <li><b>Explicit coordinates</b>: supply {@code lat} + {@code lon} directly. {@code addressLine}
 *       / {@code city} / {@code state} / {@code zip} must also be provided.
 *   <li><b>Auto-geocode</b>: supply only {@code address} (a free-text string). The server calls
 *       {@link com.teamhub.integration.geocoding.GeocodingService} to derive lat/lon and
 *       city/state/zip. {@code addressLine} is required; structured fields are optional.
 * </ol>
 */
public record CreateCustomerRequest(
    @NotBlank String name,
    @NotBlank
        @Pattern(
            regexp = "\\+[1-9]\\d{7,14}",
            message = "must be a valid E.164 phone number (e.g. +15033334444)")
        String phone,
    String email,
    String companyName,
    @NotBlank String addressLine,
    String city,
    String state,
    String zip,
    Double lat,
    Double lon,
    /** Free-text address for server-side auto-geocoding (PRD §8.1, TASK-038). */
    String address) {}
