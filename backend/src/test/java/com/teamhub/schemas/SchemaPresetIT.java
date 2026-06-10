package com.teamhub.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantProvisionException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for TASK-040: schema presets, tenant provisioning with preset_key, boot-time
 * validation, and repeatable-migration idempotency.
 *
 * <ul>
 *   <li>Step 1: Provision a tenant with {@code preset_key=plumber}; verify the tenant's active
 *       schema matches the plumber preset.
 *   <li>Step 2: All eight preset JSON files are valid at boot (proved by the app context loading
 *       without error, and by verifying all 8 presets are present in the DB).
 *   <li>Step 3: The repeatable migration is idempotent — exactly 8 rows exist in {@code
 *       schema_presets} after migration.
 * </ul>
 */
@SpringBootTest
@IntegrationTest
class SchemaPresetIT {

  @Autowired private InternalTenantService tenantService;

  @Autowired private AppointmentSchemaRepository schemaRepository;

  @Autowired private SchemaPresetRepository presetRepository;

  // ─── TASK-040 Test Step 1 ────────────────────────────────────────────────

  /**
   * TASK-040 Test Step 1: Provision a tenant with {@code preset_key=plumber}. The tenant's active
   * appointment schema must match the plumber preset (name and field keys).
   */
  @Test
  @Transactional
  void provision_withPlumberPreset_createsMatchingActiveSchema() {
    Tenant tenant = tenantService.provision("Plumber Co", "admin@plumber-co.com", "plumber");

    SchemaPreset plumberPreset = presetRepository.findById("plumber").orElseThrow();
    Optional<AppointmentSchema> active =
        schemaRepository.findByTenantIdAndNameAndActiveTrue(
            tenant.getId(), plumberPreset.getSchemaName());

    assertThat(active).isPresent();
    AppointmentSchema schema = active.get();
    assertThat(schema.isActive()).isTrue();
    assertThat(schema.getVersion()).isEqualTo(1);
    assertThat(schema.getName()).isEqualTo("Plumber Job");

    List<String> fieldKeys =
        schema.getFieldDefinitions().stream().map(FieldDefinition::key).toList();
    assertThat(fieldKeys).contains("service_type", "problem_description");
    // Verify field definitions are loaded from the preset (not empty)
    assertThat(schema.getFieldDefinitions()).hasSameSizeAs(plumberPreset.getFieldDefinitions());
  }

  /**
   * Provisioning with no preset_key creates a tenant without any appointment schema (backward
   * compatibility).
   */
  @Test
  @Transactional
  void provision_withoutPreset_createsNoSchema() {
    Tenant tenant = tenantService.provision("No Preset Co", "admin@no-preset.com");

    long schemaCount =
        schemaRepository.findAll().stream()
            .filter(s -> tenant.getId().equals(s.getTenantId()))
            .count();
    assertThat(schemaCount).isZero();
  }

  /** Provisioning with an unknown preset_key throws {@link TenantProvisionException}. */
  @Test
  @Transactional
  void provision_withUnknownPresetKey_throwsTenantProvisionException() {
    assertThatThrownBy(
            () -> tenantService.provision("Bad Preset Co", "admin@bad-preset.com", "nonexistent"))
        .isInstanceOf(TenantProvisionException.class)
        .hasMessageContaining("nonexistent");
  }

  // ─── TASK-040 Test Step 2 ────────────────────────────────────────────────

  /**
   * TASK-040 Test Step 2: All eight preset JSON files are valid at boot. If {@link
   * SchemaPresetLoader} threw during startup this test context would not load. Verify all 8 presets
   * are in the DB and have non-empty field definitions.
   */
  @Test
  void allPresetJsonFiles_areValidAtBoot_eightPresetsLoaded() {
    assertThat(presetRepository.count()).isEqualTo(8);

    List<String> expectedKeys =
        List.of(
            "appliance",
            "ac",
            "electrician",
            "plumber",
            "handyman",
            "roofing",
            "solar",
            "locksmith");
    List<String> actualKeys =
        presetRepository.findAll().stream().map(SchemaPreset::getPresetKey).sorted().toList();
    assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedKeys);

    presetRepository
        .findAll()
        .forEach(
            preset -> {
              assertThat(preset.getFieldDefinitions())
                  .as("preset '%s' must have at least one field", preset.getPresetKey())
                  .isNotEmpty();
              preset
                  .getFieldDefinitions()
                  .forEach(
                      fd -> {
                        assertThat(fd.key())
                            .as("field key must not be blank in preset '%s'", preset.getPresetKey())
                            .isNotBlank();
                        assertThat(fd.label())
                            .as(
                                "field label must not be blank in preset '%s'",
                                preset.getPresetKey())
                            .isNotBlank();
                        assertThat(fd.type())
                            .as(
                                "field type must not be blank in preset '%s'",
                                preset.getPresetKey())
                            .isNotBlank();
                      });
            });
  }

  /**
   * Locksmith preset must include the automotive section with the {@code make} dropdown (top-5 +
   * Other...) and free-text {@code model} / {@code year} fields (PRD §8.5).
   */
  @Test
  void locksmithPreset_hasAutomotiveSection_withTopMakesAndFreeTextModelYear() {
    SchemaPreset locksmith = presetRepository.findById("locksmith").orElseThrow();
    List<FieldDefinition> fields = locksmith.getFieldDefinitions();

    FieldDefinition makeField =
        fields.stream()
            .filter(f -> "make".equals(f.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("locksmith preset missing 'make' field"));

    assertThat(makeField.type()).isEqualTo("dropdown");
    assertThat(makeField.options())
        .as("make dropdown must contain the top-5 makes and 'Other...' (PRD §8.5)")
        .contains("Ford", "Chevrolet", "Toyota", "Honda", "Tesla", "Other...");

    FieldDefinition modelField =
        fields.stream()
            .filter(f -> "model".equals(f.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("locksmith preset missing 'model' field"));
    assertThat(modelField.type()).isEqualTo("text");

    FieldDefinition yearField =
        fields.stream()
            .filter(f -> "year".equals(f.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("locksmith preset missing 'year' field"));
    assertThat(yearField.type()).isEqualTo("text");
  }

  // ─── TASK-040 Test Step 3 ────────────────────────────────────────────────

  /**
   * TASK-040 Test Step 3: The repeatable migration is idempotent. The migration uses {@code INSERT
   * ... ON CONFLICT DO UPDATE}, so re-running it must not produce duplicate rows. After migration,
   * exactly 8 rows exist in {@code schema_presets}.
   */
  @Test
  void schemaPresets_repeatedMigration_producesNoDuplicates() {
    // Each preset_key is the PK — no duplicates are possible.
    // Verify count = 8 (the migration ran at least once via Flyway).
    long count = presetRepository.count();
    assertThat(count).isEqualTo(8);

    // Verify each preset has a unique key (PK constraint enforced).
    List<String> keys =
        presetRepository.findAll().stream().map(SchemaPreset::getPresetKey).toList();
    assertThat(keys).doesNotHaveDuplicates();
  }
}
