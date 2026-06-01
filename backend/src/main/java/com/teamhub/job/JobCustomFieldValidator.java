package com.teamhub.job;

import com.teamhub.schemas.AppointmentSchemaRepository;
import com.teamhub.schemas.FieldDefinition;
import com.teamhub.schemas.SchemaNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates a job's {@code custom_fields} against the required fields declared in the referenced
 * appointment schema (PRD §8.2, TASK-042).
 *
 * <p>Only the {@code required == true} constraint is enforced here; type-level validation (e.g.
 * numeric bounds, regex) is deferred to a later task.
 */
@Component
public class JobCustomFieldValidator {

  private final AppointmentSchemaRepository schemaRepository;

  public JobCustomFieldValidator(AppointmentSchemaRepository schemaRepository) {
    this.schemaRepository = schemaRepository;
  }

  /**
   * Validates that {@code customFields} contains a non-null value for every {@code required} field
   * defined in the schema identified by {@code schemaId}.
   *
   * @param tenantId the owning tenant (used to scope the schema lookup)
   * @param schemaId the UUID of the appointment schema row to validate against
   * @param customFields the submitted custom-field values (may be {@code null} — treated as empty)
   * @throws SchemaNotFoundException if no schema row with the given ID exists
   * @throws CustomFieldValidationException (HTTP 422) if any required field is absent or null
   */
  public void validate(UUID tenantId, UUID schemaId, Map<String, Object> customFields) {
    var schema =
        schemaRepository
            .findById(schemaId)
            .filter(s -> s.getTenantId().equals(tenantId))
            .orElseThrow(() -> new SchemaNotFoundException(schemaId));

    Map<String, Object> fields = customFields != null ? customFields : Map.of();

    List<String> missingFields =
        schema.getFieldDefinitions().stream()
            .filter(FieldDefinition::required)
            .map(FieldDefinition::key)
            .filter(key -> fields.get(key) == null)
            .toList();

    if (!missingFields.isEmpty()) {
      throw new CustomFieldValidationException(missingFields);
    }
  }
}
