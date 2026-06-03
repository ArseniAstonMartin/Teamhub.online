package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.exception.ApiConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link BillingServiceException} constructors (TASK-061).
 *
 * <p>Validates both constructors: the two-arg variant (wrapping a {@link
 * com.stripe.exception.StripeException}) and the single-message variant.
 */
class BillingServiceExceptionTest {

  @Test
  void constructorWithStripeException_setsMessageCauseAndCode() {
    ApiConnectionException cause = new ApiConnectionException("network error");

    BillingServiceException ex = new BillingServiceException("Stripe call failed", cause);

    assertThat(ex.getMessage()).isEqualTo("Stripe call failed");
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getCode()).isEqualTo(BillingServiceException.CODE);
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void constructorWithMessageOnly_setsMessageAndNoCause() {
    BillingServiceException ex = new BillingServiceException("precondition failed");

    assertThat(ex.getMessage()).isEqualTo("precondition failed");
    assertThat(ex.getCause()).isNull();
    assertThat(ex.getCode()).isEqualTo(BillingServiceException.CODE);
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }
}
