package com.teamhub.schemas.dto;

import com.teamhub.schemas.FieldDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/schemas} — publishes a new schema version (PRD §5.3, §8.2).
 *
 * <p>The {@code name} identifies the logical schema (e.g. {@code "Automotive Locksmith Job"}). Each
 * call inserts a new version row; existing versions are preserved for historical job validation.
 */
public record PublishSchemaRequest(
    /** Logical schema name; must match the name used in previous versions for the same tenant. */
    @NotBlank String name,

    /** Ordered field descriptors for this version; must contain at least one field. */
    @NotEmpty List<FieldDefinition> fieldDefinitions) {}
