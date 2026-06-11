package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.teamhub.auth.dto.CurrentUserResponse;
import com.teamhub.tenancy.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link CurrentUserService} (TASK-132). */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private CurrentUserService currentUserService;

  @Test
  void getCurrentUser_activeUser_returnsProfile() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    User user = new User(tenant, "tech@acme.com", UserRole.TECHNICIAN, "Tech");
    UUID userId = user.getId();

    when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

    CurrentUserResponse response = currentUserService.getCurrentUser(tenantId, userId);

    assertThat(response.email()).isEqualTo("tech@acme.com");
    assertThat(response.name()).isEqualTo("Tech");
    assertThat(response.role()).isEqualTo(UserRole.TECHNICIAN);
    assertThat(response.tenantId()).isEqualTo(tenantId);
  }

  @Test
  void getCurrentUser_unknownUser_throwsUserNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> currentUserService.getCurrentUser(tenantId, userId))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void getCurrentUser_deactivatedUser_throwsAccountDeactivated() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    User user = new User(tenant, "tech@acme.com", UserRole.TECHNICIAN, "Tech");
    user.setActive(false);
    UUID userId = user.getId();

    when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> currentUserService.getCurrentUser(tenantId, userId))
        .isInstanceOf(AccountDeactivatedException.class);
  }
}
