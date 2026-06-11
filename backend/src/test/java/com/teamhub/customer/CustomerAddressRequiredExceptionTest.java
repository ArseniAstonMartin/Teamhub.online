package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Unit tests for {@link CustomerAddressRequiredException}. */
@Tag("unit")
class CustomerAddressRequiredExceptionTest {

  @Test
  void defaultConstructor_setsCodeMessageAndStatus() {
    CustomerAddressRequiredException ex = new CustomerAddressRequiredException();

    assertThat(ex.getCode()).isEqualTo(CustomerAddressRequiredException.CODE);
    assertThat(ex.getMessage())
        .isEqualTo(
            "Customer address with coordinates is required. Provide lat/lon or a geocodable address.");
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
