package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.auth.dto.CurrentUserResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

/** Unit tests for {@link UserController#getCurrentUser} (TASK-132). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class UserControllerUnitTest {

  @Mock private UserManagementService userManagementService;

  @Mock private CurrentUserService currentUserService;

  private UserController controller() {
    return new UserController(userManagementService, currentUserService);
  }

  @Test
  void getCurrentUser_delegatesToServiceWithTenantAndUserIds() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    CurrentUserResponse expected =
        new CurrentUserResponse(
            userId,
            "tech@acme.com",
            "Tech User",
            UserRole.TECHNICIAN,
            tenantId,
            Instant.parse("2026-06-10T12:00:00Z"));
    when(currentUserService.getCurrentUser(tenantId, userId)).thenReturn(expected);

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .build();

    CurrentUserResponse response = controller().getCurrentUser(jwt);

    assertThat(response).isEqualTo(expected);
    verify(currentUserService).getCurrentUser(tenantId, userId);
  }
}
