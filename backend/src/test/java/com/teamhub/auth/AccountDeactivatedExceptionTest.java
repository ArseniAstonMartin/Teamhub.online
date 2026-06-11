package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Unit tests for {@link AccountDeactivatedException} (TASK-132). */
@Tag("unit")
class AccountDeactivatedExceptionTest {

  @Test
  void defaultConstructor_setsCodeMessageAndStatus() {
    AccountDeactivatedException ex = new AccountDeactivatedException();

    assertThat(ex.getCode()).isEqualTo(AccountDeactivatedException.CODE);
    assertThat(ex.getMessage()).isEqualTo("This account has been deactivated.");
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
