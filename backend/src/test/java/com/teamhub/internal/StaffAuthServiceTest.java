package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.teamhub.auth.InvalidCredentialsException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StaffAuthServiceTest {

  @Mock private StaffUserRepository staffUserRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private StaffJwtService staffJwtService;

  @InjectMocks private StaffAuthService staffAuthService;

  @Test
  void login_validCredentials_returnsStaffSession() {
    StaffUser staff = activeStaff();
    when(staffUserRepository.findByEmail("dev@teamhub.online")).thenReturn(Optional.of(staff));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(staffJwtService.issue(staff))
        .thenReturn(
            new StaffJwtService.StaffAccessToken("jwt-token", Instant.now().plusSeconds(900)));

    StaffAuthService.StaffSession session = staffAuthService.login("dev@teamhub.online", "secret");

    assertThat(session.accessToken()).isEqualTo("jwt-token");
  }

  @Test
  void login_unknownEmail_throwsInvalidCredentials() {
    when(staffUserRepository.findByEmail("missing@teamhub.online")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> staffAuthService.login("missing@teamhub.online", "secret"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    StaffUser staff = activeStaff();
    when(staffUserRepository.findByEmail("dev@teamhub.online")).thenReturn(Optional.of(staff));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    assertThatThrownBy(() -> staffAuthService.login("dev@teamhub.online", "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_inactiveAccount_throwsInvalidCredentials() {
    StaffUser staff = activeStaff();
    staff.setActive(false);
    when(staffUserRepository.findByEmail("dev@teamhub.online")).thenReturn(Optional.of(staff));

    assertThatThrownBy(() -> staffAuthService.login("dev@teamhub.online", "secret"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  private static StaffUser activeStaff() {
    StaffUser staff = new StaffUser("dev@teamhub.online", "hash", "Dev", StaffRole.TEAMHUB_STAFF);
    staff.setId(UUID.randomUUID());
    return staff;
  }
}
