package com.teamhub.automation;

import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.AppointmentSchemaRepository;
import com.teamhub.schemas.FieldDefinition;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Validates that every field key referenced in {@code trigger_fields}, {@code match_rules}, and
 * {@code actions} of an automation rule exists in at least one of the tenant's currently active
 * {@link AppointmentSchema}s (PRD §16.2; TASK-051).
 *
 * <p>If the tenant has no active schema, validation is skipped — the rule can still be stored, and
 * the engine will not fire it until a schema is published.
 *
 * <p>Throws {@link AutomationRuleValidationException} (422 UNPROCESSABLE_ENTITY) on the first
 * unknown key found.
 */
@Component
public class AutomationRuleValidator {

  private final AppointmentSchemaRepository schemaRepository;

  public AutomationRuleValidator(AppointmentSchemaRepository schemaRepository) {
    this.schemaRepository = schemaRepository;
  }

  /**
   * Validates field-key references in the given rule fields against the tenant's active schemas.
   *
   * @param tenantId the tenant that owns the rule
   * @param triggerFields list of field keys that trigger evaluation; may be {@code null}
   * @param matchRules key-value pairs whose keys must be known field keys; may be {@code null}
   * @param actions key-value pairs whose keys must be known field keys; may be {@code null}
   * @throws AutomationRuleValidationException when any key is not found in the active schema
   */
  public void validate(
      UUID tenantId,
      List<String> triggerFields,
      Map<String, Object> matchRules,
      Map<String, Object> actions) {

    List<AppointmentSchema> activeSchemas = schemaRepository.findByTenantIdAndActiveTrue(tenantId);
    if (activeSchemas.isEmpty()) {
      return;
    }

    Set<String> knownKeys =
        activeSchemas.stream()
            .map(AppointmentSchema::getFieldDefinitions)
            .flatMap(Collection::stream)
            .map(FieldDefinition::key)
            .collect(Collectors.toSet());

    if (triggerFields != null) {
      for (String key : triggerFields) {
        if (!knownKeys.contains(key)) {
          throw new AutomationRuleValidationException(
              "trigger_fields key '" + key + "' is not defined in any active appointment schema");
        }
      }
    }

    if (matchRules != null) {
      for (String key : matchRules.keySet()) {
        if (!knownKeys.contains(key)) {
          throw new AutomationRuleValidationException(
              "match_rules key '" + key + "' is not defined in any active appointment schema");
        }
      }
    }

    if (actions != null) {
      for (String key : actions.keySet()) {
        if (!knownKeys.contains(key)) {
          throw new AutomationRuleValidationException(
              "actions key '" + key + "' is not defined in any active appointment schema");
        }
      }
    }
  }
}
