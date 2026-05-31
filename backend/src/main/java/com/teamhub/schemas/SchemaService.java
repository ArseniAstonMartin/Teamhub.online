package com.teamhub.schemas;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for appointment-schema versioning and validation (PRD §8.2, §8.3).
 *
 * <h3>Versioning model</h3>
 *
 * <p>The {@code appointment_schemas} table is append-only. Each call to {@link
 * #publishNewVersion(UUID, String, List)} inserts a new row with {@code version = max(version)+1}
 * and {@code is_active = true}. The previous active row's {@code is_active} is set to {@code false}
 * (single-active semantics — only one version is active per tenant+name pair at any time). Old rows
 * are never deleted so that historical jobs can still reference and validate against their {@code
 * schema_version} (PRD §8.2).
 *
 * <h3>Validation</h3>
 *
 * <p>Before persisting, {@link #publishNewVersion} validates the field definitions:
 *
 * <ol>
 *   <li>A field's {@code depends_on} value, when non-null, must equal the {@code key} of another
 *       field in the same schema.
 *   <li>Every key in {@code options_by_parent} must be present in the parent field's {@code
 *       options} list.
 * </ol>
 *
 * <p>Violations throw {@link SchemaValidationException} (422 UNPROCESSABLE_ENTITY).
 */
@Service
@Transactional
public class SchemaService {

  private final AppointmentSchemaRepository schemaRepository;

  public SchemaService(AppointmentSchemaRepository schemaRepository) {
    this.schemaRepository = schemaRepository;
  }

  /**
   * Publishes a new version of an appointment schema.
   *
   * <p>Steps:
   *
   * <ol>
   *   <li>Validate {@code fieldDefinitions} (depends_on references + options_by_parent keys).
   *   <li>Determine {@code nextVersion = max(existing version) + 1} (or 1 if none exist).
   *   <li>Set all previously active rows for this tenant + name to {@code is_active = false}.
   *   <li>Insert a new row with {@code version = nextVersion}, {@code is_active = true}.
   * </ol>
   *
   * @param tenantId the tenant that owns the schema
   * @param name schema name (e.g. {@code "Automotive Locksmith Job"})
   * @param fieldDefinitions ordered list of field descriptors; must not be {@code null}
   * @return the persisted {@link AppointmentSchema} with its assigned id and version
   * @throws SchemaValidationException if any field-definition constraint is violated
   */
  public AppointmentSchema publishNewVersion(
      UUID tenantId, String name, List<FieldDefinition> fieldDefinitions) {

    validateFieldDefinitions(fieldDefinitions);

    int nextVersion =
        schemaRepository.findMaxVersionByTenantIdAndName(tenantId, name).map(v -> v + 1).orElse(1);

    schemaRepository.deactivateAllByTenantIdAndName(tenantId, name);

    AppointmentSchema schema = new AppointmentSchema();
    schema.setTenantId(tenantId);
    schema.setName(name);
    schema.setVersion(nextVersion);
    schema.setActive(true);
    schema.setFieldDefinitions(fieldDefinitions);

    return schemaRepository.save(schema);
  }

  /**
   * Returns the schema at a specific version for the given tenant. Intended for job-validation
   * logic: a job stores {@code schema_version} and this method retrieves the field definitions that
   * were active when the job was created, so validation uses the correct version regardless of what
   * the current active version is (PRD §8.2).
   *
   * @throws SchemaNotFoundException when no schema with the given version exists
   */
  @Transactional(readOnly = true)
  public AppointmentSchema getByVersion(UUID tenantId, String name, int version) {
    return schemaRepository
        .findByTenantIdAndNameAndVersion(tenantId, name, version)
        .orElseThrow(() -> new SchemaNotFoundException(name, version));
  }

  /**
   * Returns all schema versions for the given tenant, ordered by name then version descending.
   * Intended for the admin list endpoint (PRD §5.3, §8.2).
   */
  @Transactional(readOnly = true)
  public List<AppointmentSchema> listByTenant(UUID tenantId) {
    return schemaRepository.findAllByTenantIdOrderByNameAscVersionDesc(tenantId);
  }

  /**
   * Marks the schema with the given {@code id} as inactive ({@code is_active = false}).
   *
   * <p>Deactivation never deletes rows — the record remains for historical job-validation purposes
   * (PRD §8.2). Only the schema owner's tenant may deactivate its own schemas.
   *
   * @param tenantId the requesting tenant
   * @param id the schema row to deactivate
   * @return the updated (now-inactive) schema
   * @throws SchemaNotFoundException when the id does not exist within this tenant
   */
  public AppointmentSchema deactivateById(UUID tenantId, UUID id) {
    AppointmentSchema schema =
        schemaRepository
            .findById(id)
            .filter(s -> s.getTenantId().equals(tenantId))
            .orElseThrow(() -> new SchemaNotFoundException(id));
    schema.setActive(false);
    return schemaRepository.save(schema);
  }

  /**
   * Validates field definitions before persisting.
   *
   * <p>Rules:
   *
   * <ol>
   *   <li>{@code depends_on}, when non-null, must reference an existing field key.
   *   <li>Every key in {@code options_by_parent} must be a valid option of the referenced parent
   *       field ({@code options_by_parent} keys ⊆ parent.options).
   * </ol>
   *
   * @throws SchemaValidationException on the first constraint violation found
   */
  private void validateFieldDefinitions(List<FieldDefinition> fieldDefinitions) {
    Set<String> allKeys =
        fieldDefinitions.stream().map(FieldDefinition::key).collect(Collectors.toSet());

    for (FieldDefinition fd : fieldDefinitions) {
      String dependsOn = fd.dependsOn();
      if (dependsOn != null && !allKeys.contains(dependsOn)) {
        throw new SchemaValidationException(
            "Field '"
                + fd.key()
                + "' has depends_on '"
                + dependsOn
                + "' which does not reference an existing field key in this schema");
      }

      Map<String, List<String>> optionsByParent = fd.optionsByParent();
      if (optionsByParent != null && !optionsByParent.isEmpty()) {
        if (dependsOn == null) {
          throw new SchemaValidationException(
              "Field '" + fd.key() + "' declares options_by_parent but has no depends_on");
        }

        List<String> parentOptions =
            fieldDefinitions.stream()
                .filter(f -> f.key().equals(dependsOn))
                .findFirst()
                .map(FieldDefinition::options)
                .orElse(null);

        if (parentOptions != null) {
          for (String optionKey : optionsByParent.keySet()) {
            if (!parentOptions.contains(optionKey)) {
              throw new SchemaValidationException(
                  "Field '"
                      + fd.key()
                      + "' has options_by_parent key '"
                      + optionKey
                      + "' which is not a valid option of parent field '"
                      + dependsOn
                      + "'");
            }
          }
        }
      }
    }
  }
}
