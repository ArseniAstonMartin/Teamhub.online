package com.teamhub.schemas;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown by {@link SchemaService} when field-definition validation fails (PRD §8.2, §8.3).
 *
 * <p>Covers two cases:
 *
 * <ol>
 *   <li>{@code depends_on} references a field key that does not exist in the schema.
 *   <li>An {@code options_by_parent} key is not a valid option of the referenced parent field.
 * </ol>
 */
public class SchemaValidationException extends DomainException {

  public static final String CODE = "SCHEMA_VALIDATION_FAILED";

  public SchemaValidationException(String message) {
    super(CODE, HttpStatus.UNPROCESSABLE_ENTITY, message);
  }
}
