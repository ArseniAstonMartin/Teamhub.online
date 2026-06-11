package com.teamhub.tenancy;

import java.util.Set;

/** Allowed IANA time zones for tenant settings (matches web Settings → Tenant UI). */
public final class TenantTimezones {

  public static final String DEFAULT = "America/New_York";

  private static final Set<String> ALLOWED =
      Set.of(
          "America/New_York",
          "America/Chicago",
          "America/Denver",
          "America/Los_Angeles",
          "America/Phoenix",
          "America/Anchorage",
          "Pacific/Honolulu");

  private TenantTimezones() {}

  public static boolean isAllowed(String timezone) {
    return timezone != null && ALLOWED.contains(timezone);
  }
}
