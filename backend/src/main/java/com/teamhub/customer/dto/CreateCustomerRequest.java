package com.teamhub.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/customers} (PRD §11.3).
 *
 * <p>{@code lat} and {@code lon} are the WGS-84 coordinates of the service address. For MVP the
 * client supplies these directly.
 *
 * <p>TO-DO (PRD §11.3): When the Google Maps Geocoding integration lands, derive {@code lat}/{@code
 * lon} from {@code addressLine}/{@code city}/{@code state}/{@code zip} server-side and remove the
 * coordinate fields from this request DTO.
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
    @NotBlank String city,
    @NotBlank String state,
    @NotBlank String zip,
    @NotNull Double lat,
    @NotNull Double lon) {}
