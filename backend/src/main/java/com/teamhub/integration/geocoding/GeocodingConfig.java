package com.teamhub.integration.geocoding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wires Google Maps configuration properties for the geocoding integration (TASK-038). */
@Configuration
@EnableConfigurationProperties(GoogleMapsProperties.class)
class GeocodingConfig {}
