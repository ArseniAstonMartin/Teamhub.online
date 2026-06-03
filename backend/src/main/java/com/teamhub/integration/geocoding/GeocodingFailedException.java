package com.teamhub.integration.geocoding;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the Google Maps Geocoding API returns a non-OK status or produces no results, or when
 * the API key is not configured (PRD §8.1, TASK-038).
 */
public class GeocodingFailedException extends DomainException {

  public static final String CODE = "GEOCODE_FAILED";

  public GeocodingFailedException(String message) {
    super(CODE, HttpStatus.BAD_REQUEST, message);
  }
}
