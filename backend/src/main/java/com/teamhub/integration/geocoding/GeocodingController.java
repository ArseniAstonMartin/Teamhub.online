package com.teamhub.integration.geocoding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TO-DO (TASK-006): implement <AddressAutocomplete> component using Google Places JS SDK

/**
 * Geocoding endpoint — resolves a free-text address into city/state/zip + lat/lon (PRD §8.1,
 * TASK-038).
 *
 * <p>Requires any authenticated user (ADMIN, DISPATCHER, TECHNICIAN). Results are derived from the
 * Google Maps Geocoding API server-side so the API key is never exposed to the client.
 */
@RestController
@RequestMapping("/api/v1/geocode")
@Tag(name = "geocoding", description = "Address geocoding via Google Maps — PRD §8.1")
public class GeocodingController {

  private final GeocodingService geocodingService;

  public GeocodingController(GeocodingService geocodingService) {
    this.geocodingService = geocodingService;
  }

  /**
   * Resolves a free-text address to structured city/state/zip and WGS-84 coordinates.
   *
   * @param request address string to geocode
   * @return geocode result with city, state, zip, lat, lon
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Geocode an address",
      description =
          "Resolves a free-text address to city, state, zip, latitude, and longitude"
              + " using Google Maps Geocoding API (PRD §8.1).")
  public GeocodeResult geocode(@Valid @RequestBody GeocodeRequest request) {
    return geocodingService.geocode(request.address());
  }
}
