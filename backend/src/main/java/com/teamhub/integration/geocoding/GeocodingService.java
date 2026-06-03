package com.teamhub.integration.geocoding;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Calls the Google Maps Geocoding API to resolve a free-text address into structured address
 * components and WGS-84 coordinates (PRD §8.1, §11.3, TASK-038).
 *
 * <p>Uses Spring's {@link RestClient} (Spring Boot 3.x). The underlying {@link RestClient} is
 * created via {@link RestClient.Builder} so tests can intercept calls via {@code
 * MockRestServiceServer.bindTo(RestClient.Builder)}.
 */
@Service
public class GeocodingService {

  private static final String GEOCODE_URL =
      "https://maps.googleapis.com/maps/api/geocode/json?address={address}&key={key}";

  private final RestClient restClient;

  private final GoogleMapsProperties props;

  public GeocodingService(RestClient.Builder restClientBuilder, GoogleMapsProperties props) {
    this.restClient = restClientBuilder.build();
    this.props = props;
  }

  /**
   * Geocodes a free-text address using the Google Maps Geocoding API.
   *
   * @param address the human-readable address (e.g. "1600 Amphitheatre Parkway, Mountain View, CA")
   * @return structured geocode result with city, state, zip, lat, lon
   * @throws GeocodingFailedException when the API key is blank, the API returns a non-OK status, or
   *     no results are found
   */
  @SuppressWarnings("unchecked")
  public GeocodeResult geocode(String address) {
    String key = props.mapsApiKey();
    if (key == null || key.isBlank()) {
      throw new GeocodingFailedException(
          "Google Maps API key is not configured. Set GOOGLE_MAPS_API_KEY to enable geocoding.");
    }

    Map<String, Object> response =
        restClient.get().uri(GEOCODE_URL, address, key).retrieve().body(Map.class);

    if (response == null) {
      throw new GeocodingFailedException("Empty response from Google Maps Geocoding API.");
    }

    String status = (String) response.get("status");
    if (!"OK".equals(status)) {
      throw new GeocodingFailedException(
          "Geocoding failed with status: " + status + " for address: " + address);
    }

    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
    if (results == null || results.isEmpty()) {
      throw new GeocodingFailedException("No geocoding results for address: " + address);
    }

    Map<String, Object> first = results.get(0);
    List<Map<String, Object>> components =
        (List<Map<String, Object>>) first.get("address_components");
    Map<String, Map<String, Double>> geometry =
        (Map<String, Map<String, Double>>) first.get("geometry");

    String city = extractComponent(components, "locality");
    String state = extractShortName(components, "administrative_area_level_1");
    String zip = extractComponent(components, "postal_code");

    Map<String, Double> location = geometry.get("location");
    double lat = location.get("lat");
    double lon = location.get("lng");

    return new GeocodeResult(city, state, zip, lat, lon);
  }

  @SuppressWarnings("unchecked")
  private String extractComponent(List<Map<String, Object>> components, String type) {
    if (components == null) {
      return null;
    }
    return components.stream()
        .filter(c -> ((List<String>) c.get("types")).contains(type))
        .map(c -> (String) c.get("long_name"))
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private String extractShortName(List<Map<String, Object>> components, String type) {
    if (components == null) {
      return null;
    }
    return components.stream()
        .filter(c -> ((List<String>) c.get("types")).contains(type))
        .map(c -> (String) c.get("short_name"))
        .findFirst()
        .orElse(null);
  }
}
