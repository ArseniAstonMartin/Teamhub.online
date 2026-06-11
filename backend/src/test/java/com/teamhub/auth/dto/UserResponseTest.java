package com.teamhub.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRole;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserResponse} (TASK-129). */
@Tag("unit")
class UserResponseTest {

  @Test
  void from_mapsLastLoginAtWhenPresent() {
    Tenant tenant = new Tenant("Acme");
    User user = new User(tenant, "tech@acme.com", UserRole.TECHNICIAN, "Tech");
    Instant lastLogin = Instant.parse("2026-06-10T12:00:00Z");
    user.setLastLoginAt(lastLogin);

    UserResponse response = UserResponse.from(user);

    assertThat(response.lastLoginAt()).isEqualTo(lastLogin);
    assertThat(response.email()).isEqualTo("tech@acme.com");
    assertThat(response.role()).isEqualTo(UserRole.TECHNICIAN);
  }

  @Test
  void from_nullLastLoginAt_returnsNull() {
    Tenant tenant = new Tenant("Acme");
    User user = new User(tenant, "invited@acme.com", UserRole.DISPATCHER, "Invited");

    UserResponse response = UserResponse.from(user);

    assertThat(response.lastLoginAt()).isNull();
  }
}
