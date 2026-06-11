package com.teamhub.internal;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the internal admin API (PRD §6.4, TASK-131). */
@ConfigurationProperties(prefix = "teamhub.internal")
public class InternalProperties {

  /** Comma-separated list of client IPs allowed to reach {@code /api/v1/internal/**}. */
  private String allowedIps = "127.0.0.1,::1";

  public String getAllowedIps() {
    return allowedIps;
  }

  public void setAllowedIps(String allowedIps) {
    this.allowedIps = allowedIps;
  }

  /** Parsed allow-list for fast membership checks. */
  public Set<String> allowedIpSet() {
    return Arrays.stream(allowedIps.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
