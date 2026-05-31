package com.teamhub.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * A single custom-field descriptor stored inside {@link AppointmentSchema#getFieldDefinitions()}.
 *
 * <p>JSON keys follow snake_case (PRD §8.2). Jackson deserializes this record from the JSONB column
 * via Hibernate 6's built-in JSON type.
 *
 * <p>Supported types: {@code dropdown}, {@code text}, {@code number}, {@code date}, {@code time},
 * {@code datetime}, {@code file}, {@code image}, {@code video}, {@code list}, {@code boolean}.
 */
public record FieldDefinition(
    /** Unique key within the schema (e.g. {@code "vehicle_make"}). */
    String key,

    /** Human-readable label shown in the UI. */
    String label,

    /**
     * Field type. One of: dropdown | text | number | date | time | datetime | file | image | video
     * | list | boolean.
     */
    String type,

    /** Whether the field is required when filling out a job form. */
    boolean required,

    /** Display order within the form (lower = higher). */
    int order,

    /** Fixed list of allowed values for {@code dropdown} fields. */
    List<String> options,

    /**
     * Key of the parent field this field depends on. When non-null, this field is disabled until
     * the parent is filled in (PRD §8.3). Must reference an existing field {@code key} within the
     * same schema.
     */
    @JsonProperty("depends_on") String dependsOn,

    /**
     * Maps each parent-field value to the list of permitted child values (PRD §8.3). Every key in
     * this map must be a valid option of the parent field ({@code options_by_parent} keys ⊆
     * parent.options).
     */
    @JsonProperty("options_by_parent") Map<String, List<String>> optionsByParent,

    /** Optional min / max / regex constraints for text and number fields. */
    FieldValidation validation) {}
