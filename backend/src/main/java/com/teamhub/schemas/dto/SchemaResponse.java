package com.teamhub.schemas.dto;

import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.FieldDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response shape for a single {@link AppointmentSchema} row (PRD §8.2).
 *
 * <p>Returned by all {@code /api/v1/schemas} endpoints.
 */
public record SchemaResponse(
    UUID id,
    String name,
    int version,
    boolean active,
    List<FieldDefinition> fieldDefinitions,
    Instant createdAt) {

  /** Maps an {@link AppointmentSchema} entity to its API response representation. */
  public static SchemaResponse from(AppointmentSchema schema) {
    return new SchemaResponse(
        schema.getId(),
        schema.getName(),
        schema.getVersion(),
        schema.isActive(),
        schema.getFieldDefinitions(),
        schema.getCreatedAt());
  }
}
