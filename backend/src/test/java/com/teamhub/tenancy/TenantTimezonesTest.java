package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TenantTimezones} (TASK-128). */
@Tag("unit")
class TenantTimezonesTest {

  @Test
  void isAllowed_usZone_returnsTrue() {
    assertThat(TenantTimezones.isAllowed("America/Chicago")).isTrue();
  }

  @Test
  void isAllowed_unknownZone_returnsFalse() {
    assertThat(TenantTimezones.isAllowed("Europe/London")).isFalse();
  }

  @Test
  void isAllowed_null_returnsFalse() {
    assertThat(TenantTimezones.isAllowed(null)).isFalse();
  }
}
