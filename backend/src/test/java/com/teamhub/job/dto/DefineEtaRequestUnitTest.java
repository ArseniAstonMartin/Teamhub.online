package com.teamhub.job.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Bean-validation unit tests for {@link DefineEtaRequest} (TASK-135). */
@Tag("unit")
class DefineEtaRequestUnitTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void validEtaMinutes_passesValidation() {
    Set<ConstraintViolation<DefineEtaRequest>> violations =
        validator.validate(new DefineEtaRequest(45));

    assertThat(violations).isEmpty();
  }

  @Test
  void nullEtaMinutes_failsValidation() {
    Set<ConstraintViolation<DefineEtaRequest>> violations =
        validator.validate(new DefineEtaRequest(null));

    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("etaMinutes");
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 0, 481, 500})
  void etaMinutesOutOfRange_failsValidation(int etaMinutes) {
    Set<ConstraintViolation<DefineEtaRequest>> violations =
        validator.validate(new DefineEtaRequest(etaMinutes));

    assertThat(violations).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {5, 480})
  void boundaryEtaMinutes_passesValidation(int etaMinutes) {
    Set<ConstraintViolation<DefineEtaRequest>> violations =
        validator.validate(new DefineEtaRequest(etaMinutes));

    assertThat(violations).isEmpty();
  }
}
