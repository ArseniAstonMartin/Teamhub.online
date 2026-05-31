package com.teamhub.schemas;

/**
 * Optional validation constraints for a {@link FieldDefinition} (PRD §8.2).
 *
 * <p>All fields are nullable; null means the constraint is not applied.
 */
public record FieldValidation(Number min, Number max, String regex) {}
