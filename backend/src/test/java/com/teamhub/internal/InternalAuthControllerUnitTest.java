package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.teamhub.internal.dto.InternalLoginRequest;
import com.teamhub.internal.dto.StaffTokenResponse;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalAuthControllerUnitTest {

  @Mock private StaffAuthService staffAuthService;

  @InjectMocks private InternalAuthController controller;

  @Test
  void login_delegatesToStaffAuthService() {
    when(staffAuthService.login("dev@teamhub.online", "secret"))
        .thenReturn(new StaffAuthService.StaffSession("jwt", Instant.now().plusSeconds(900)));

    StaffTokenResponse response =
        controller.login(new InternalLoginRequest("dev@teamhub.online", "secret"));

    assertThat(response.accessToken()).isEqualTo("jwt");
    assertThat(response.tokenType()).isEqualTo("Bearer");
  }
}
