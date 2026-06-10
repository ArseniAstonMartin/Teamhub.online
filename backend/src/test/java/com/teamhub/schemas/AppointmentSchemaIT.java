package com.teamhub.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link SchemaService} covering all three TASK-039 test steps.
 *
 * <ul>
 *   <li>Step 1: publish v1 then v2; v1 becomes inactive.
 *   <li>Step 2: depends_on referencing a non-existent key throws {@link SchemaValidationException}.
 *   <li>Step 3: {@link SchemaService#getByVersion} retrieves a historical version even after a
 *       newer version is active — simulating a job that stores schema_version=1 and validates
 *       against v1, not v2 (PRD §8.2).
 * </ul>
 */
@SpringBootTest
@IntegrationTest
@Transactional
class AppointmentSchemaIT {

  private static final String SCHEMA_NAME = "Automotive Locksmith Job";

  @Autowired private SchemaService schemaService;

  @Autowired private AppointmentSchemaRepository schemaRepository;

  @Autowired private InternalTenantService tenantService;

  private java.util.UUID tenantId;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantService.provision("Schema Test Co", "admin@schema-test.com");
    tenantId = tenant.getId();
  }

  // ---------------------------------------------------------------------------
  // Step 1: publish v1 → publish v2; v1 becomes inactive
  // ---------------------------------------------------------------------------

  /**
   * TASK-039 Test Step 1: Publish v1 with field {@code make}; publish v2 adding {@code model}; v1
   * is no longer active.
   */
  @Test
  void publishNewVersion_v1ThenV2_v1BecomesInactiveAndV2IsActive() {
    FieldDefinition makeField = makeField("make", "Vehicle Make", 1);

    AppointmentSchema v1 =
        schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(makeField));

    assertThat(v1.getVersion()).isEqualTo(1);
    assertThat(v1.isActive()).isTrue();
    assertThat(v1.getFieldDefinitions()).hasSize(1);
    assertThat(v1.getFieldDefinitions().get(0).key()).isEqualTo("make");

    FieldDefinition modelField = makeField("model", "Vehicle Model", 2);
    AppointmentSchema v2 =
        schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(makeField, modelField));

    assertThat(v2.getVersion()).isEqualTo(2);
    assertThat(v2.isActive()).isTrue();
    assertThat(v2.getFieldDefinitions()).hasSize(2);

    // v1 must now be inactive
    AppointmentSchema v1Refreshed =
        schemaRepository.findByTenantIdAndNameAndVersion(tenantId, SCHEMA_NAME, 1).orElseThrow();
    assertThat(v1Refreshed.isActive()).isFalse();
  }

  // ---------------------------------------------------------------------------
  // Step 2: depends_on referencing a non-existent key → domain error
  // ---------------------------------------------------------------------------

  /**
   * TASK-039 Test Step 2: Attempt to publish a schema with {@code depends_on: 'nonexistent_field'}
   * — must throw {@link SchemaValidationException} (DomainException).
   */
  @Test
  void publishNewVersion_dependsOnNonexistentField_throwsSchemaValidationException() {
    FieldDefinition badField =
        new FieldDefinition(
            "model",
            "Vehicle Model",
            "dropdown",
            false,
            2,
            null,
            "nonexistent_field", // depends_on references a key that is not in the schema
            null,
            null);

    assertThatThrownBy(
            () -> schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(badField)))
        .isInstanceOf(SchemaValidationException.class)
        .hasMessageContaining("nonexistent_field");
  }

  // ---------------------------------------------------------------------------
  // Step 3: historical job stores schema_version=1 and validates against v1
  // ---------------------------------------------------------------------------

  /**
   * TASK-039 Test Step 3: A historical job that stores {@code schema_version = 1} can retrieve and
   * validate against v1's field definitions even after v2 is active. This proves that job
   * validation is version-pinned and not affected by schema evolution.
   */
  @Test
  void getByVersion_returnsHistoricalV1FieldsAfterV2Published() {
    FieldDefinition makeField = makeField("make", "Vehicle Make", 1);
    schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(makeField));

    FieldDefinition modelField = makeField("model", "Vehicle Model", 2);
    schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(makeField, modelField));

    // Simulate a historical job: schema_version = 1
    AppointmentSchema v1 = schemaService.getByVersion(tenantId, SCHEMA_NAME, 1);
    AppointmentSchema v2 = schemaService.getByVersion(tenantId, SCHEMA_NAME, 2);

    // v1 has only the 'make' field — the job validates against this, ignoring 'model'
    List<String> v1Keys = v1.getFieldDefinitions().stream().map(FieldDefinition::key).toList();
    assertThat(v1Keys).containsExactly("make");
    assertThat(v1Keys).doesNotContain("model");

    // v2 has both fields — new jobs use the current active schema
    List<String> v2Keys = v2.getFieldDefinitions().stream().map(FieldDefinition::key).toList();
    assertThat(v2Keys).containsExactlyInAnyOrder("make", "model");

    // v1 is no longer active but is still retrievable (append-only table)
    assertThat(v1.isActive()).isFalse();
    assertThat(v2.isActive()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional: options_by_parent validation
  // ---------------------------------------------------------------------------

  @Test
  void publishNewVersion_optionsByParentWithInvalidKey_throwsSchemaValidationException() {
    FieldDefinition makeField =
        new FieldDefinition(
            "make",
            "Vehicle Make",
            "dropdown",
            true,
            1,
            List.of("Ford", "Toyota"), // valid parent options
            null,
            null,
            null);

    FieldDefinition modelField =
        new FieldDefinition(
            "model",
            "Vehicle Model",
            "dropdown",
            false,
            2,
            null,
            "make",
            Map.of(
                "Ford", List.of("F-150", "Focus"),
                "Honda", List.of("Civic") // "Honda" is NOT in make.options → invalid
                ),
            null);

    assertThatThrownBy(
            () ->
                schemaService.publishNewVersion(
                    tenantId, SCHEMA_NAME, List.of(makeField, modelField)))
        .isInstanceOf(SchemaValidationException.class)
        .hasMessageContaining("Honda");
  }

  @Test
  void publishNewVersion_validOptionsWithDependentDropdown_persists() {
    FieldDefinition makeField =
        new FieldDefinition(
            "make",
            "Vehicle Make",
            "dropdown",
            true,
            1,
            List.of("Ford", "Toyota"),
            null,
            null,
            null);

    FieldDefinition modelField =
        new FieldDefinition(
            "model",
            "Vehicle Model",
            "dropdown",
            false,
            2,
            null,
            "make",
            Map.of(
                "Ford", List.of("F-150", "Focus"),
                "Toyota", List.of("Camry", "Corolla")),
            null);

    AppointmentSchema schema =
        schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(makeField, modelField));

    assertThat(schema.getId()).isNotNull();
    assertThat(schema.getVersion()).isEqualTo(1);
    assertThat(schema.getFieldDefinitions()).hasSize(2);

    FieldDefinition persistedModel =
        schema.getFieldDefinitions().stream()
            .filter(f -> "model".equals(f.key()))
            .findFirst()
            .orElseThrow();
    assertThat(persistedModel.dependsOn()).isEqualTo("make");
    assertThat(persistedModel.optionsByParent()).containsKey("Ford");
  }

  // ---------------------------------------------------------------------------
  // SchemaNotFoundException coverage
  // ---------------------------------------------------------------------------

  /**
   * Requesting a non-existent version must throw {@link SchemaNotFoundException} with a message
   * that includes the schema name and the missing version number.
   */
  @Test
  void getByVersion_nonexistentVersion_throwsSchemaNotFoundException() {
    assertThatThrownBy(() -> schemaService.getByVersion(tenantId, SCHEMA_NAME, 99))
        .isInstanceOf(SchemaNotFoundException.class)
        .hasMessageContaining(SCHEMA_NAME)
        .hasMessageContaining("99");
  }

  // ---------------------------------------------------------------------------
  // SchemaService edge-case branches
  // ---------------------------------------------------------------------------

  /**
   * A field that declares {@code options_by_parent} but omits {@code depends_on} must fail with
   * {@link SchemaValidationException} before any DB write. Covers the guard on line 130.
   */
  @Test
  void publishNewVersion_optionsByParentWithoutDependsOn_throwsSchemaValidationException() {
    FieldDefinition badField =
        new FieldDefinition(
            "model",
            "Vehicle Model",
            "dropdown",
            false,
            1,
            null,
            null, // depends_on is null — but options_by_parent is present
            Map.of("Ford", List.of("F-150")),
            null);

    assertThatThrownBy(
            () -> schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(badField)))
        .isInstanceOf(SchemaValidationException.class)
        .hasMessageContaining("options_by_parent")
        .hasMessageContaining("depends_on");
  }

  /**
   * A field whose {@code options_by_parent} is an empty map (non-null, but empty) must not trigger
   * the depends_on check and must be persisted successfully. Covers the {@code
   * !optionsByParent.isEmpty()} false branch (line 128) in {@link
   * SchemaService#validateFieldDefinitions}.
   */
  @Test
  void publishNewVersion_emptyOptionsByParent_schemaPersistedSuccessfully() {
    FieldDefinition field =
        new FieldDefinition(
            "status",
            "Status",
            "dropdown",
            false,
            1,
            List.of("open", "closed"),
            null,
            Map.of(), // non-null but empty → validation block skipped
            null);

    AppointmentSchema schema =
        schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(field));

    assertThat(schema.getId()).isNotNull();
    assertThat(schema.getFieldDefinitions()).hasSize(1);
  }

  /**
   * When the parent field referenced by {@code depends_on} has {@code null} options (e.g. a text
   * field), the {@code options_by_parent} key-set check is skipped and the schema is saved
   * successfully. Covers the {@code if (parentOptions != null)} false branch in {@link
   * SchemaService#validateFieldDefinitions}.
   */
  @Test
  void publishNewVersion_optionsByParentParentHasNullOptions_schemaPersistedSuccessfully() {
    FieldDefinition parentTextField =
        new FieldDefinition("category", "Category", "text", true, 1, null, null, null, null);

    FieldDefinition childField =
        new FieldDefinition(
            "sub_category",
            "Sub-category",
            "dropdown",
            false,
            2,
            null,
            "category", // depends_on a text field whose options == null
            Map.of("residential", List.of("kitchen", "bathroom")),
            null);

    AppointmentSchema schema =
        schemaService.publishNewVersion(
            tenantId, SCHEMA_NAME, List.of(parentTextField, childField));

    assertThat(schema.getId()).isNotNull();
    assertThat(schema.getFieldDefinitions()).hasSize(2);
  }

  // ---------------------------------------------------------------------------
  // AppointmentSchema @PrePersist branch: createdAt already set
  // ---------------------------------------------------------------------------

  /**
   * When an {@link AppointmentSchema} row is saved with {@code createdAt} pre-set, the
   * {@code @PrePersist} callback must leave the value unchanged (covers the {@code createdAt !=
   * null} branch that skips the {@code Instant.now()} assignment).
   */
  @Test
  void save_withPresetCreatedAt_prePersistDoesNotOverwriteTimestamp() {
    java.time.Instant fixed =
        java.time.Instant.parse("2025-01-01T00:00:00Z")
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    AppointmentSchema schema = new AppointmentSchema();
    schema.setTenantId(tenantId);
    schema.setName(SCHEMA_NAME);
    schema.setVersion(7);
    schema.setActive(true);
    schema.setCreatedAt(fixed);
    schema.setFieldDefinitions(List.of(makeField("x", "X", 1)));

    AppointmentSchema saved = schemaRepository.save(schema);

    assertThat(saved.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        .isEqualTo(fixed);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Builds a minimal text FieldDefinition with the given key, label, and order. */
  private static FieldDefinition makeField(String key, String label, int order) {
    return new FieldDefinition(key, label, "text", false, order, null, null, null, null);
  }
}
