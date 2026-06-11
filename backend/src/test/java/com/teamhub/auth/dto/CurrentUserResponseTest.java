package com.teamhub.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrentUserResponse} (TASK-132). */
@Tag("unit")
class CurrentUserResponseTest {

  @Test
  void from_mapsAllFields() {
    Tenant tenant = new Tenant("Acme Field");
    User user = new User(tenant, "tech@acme.com", UserRole.TECHNICIAN, "Tech User");
    Instant lastLogin = Instant.parse("2026-06-10T12:00:00Z");
    user.setLastLoginAt(lastLogin);

    CurrentUserResponse response = CurrentUserResponse.from(user);

    assertThat(response.id()).isEqualTo(user.getId());
    assertThat(response.email()).isEqualTo("tech@acme.com");
    assertThat(response.name()).isEqualTo("Tech User");
    assertThat(response.role()).isEqualTo(UserRole.TECHNICIAN);
    assertThat(response.tenantId()).isEqualTo(tenant.getId());
    assertThat(response.lastLoginAt()).isEqualTo(lastLogin);
  }

  @Test
  void from_nullLastLoginAt_returnsNull() {
    Tenant tenant = new Tenant("Acme Field");
    User user = new User(tenant, "invited@acme.com", UserRole.DISPATCHER, "Invited");

    CurrentUserResponse response = CurrentUserResponse.from(user);

    assertThat(response.lastLoginAt()).isNull();
    assertThat(response.tenantId()).isEqualTo(tenant.getId());
  }
}
