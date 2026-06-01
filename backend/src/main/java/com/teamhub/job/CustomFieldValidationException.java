package com.teamhub.job;

import com.teamhub.exception.DomainException;
import java.util.Collection;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a job's {@code custom_fields} map is missing one or more fields that are marked
 * {@code required = true} in the referenced appointment schema (PRD §8.2, TASK-042).
 *
 * <p>HTTP 422 Unprocessable Entity.
 */
public class CustomFieldValidationException extends DomainException {

  public static final String CODE = "CUSTOM_FIELD_VALIDATION_ERROR";

  /**
   * @param missingFields the set of field keys whose values are absent or null in the submitted
   *     custom_fields map
   */
  public CustomFieldValidationException(Collection<String> missingFields) {
    super(
        CODE,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Missing required custom fields: " + String.join(", ", missingFields));
  }
}
