package com.teamhub.schemas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A predefined appointment-schema template seeded into the database by the Flyway repeatable
 * migration {@code R__seed_schema_presets.sql} (PRD §8.4).
 *
 * <p>Schema presets are global reference data (not tenant-scoped) read during tenant provisioning.
 * They are not under RLS. {@code SELECT} is granted to {@code teamhub_app}.
 */
@Entity
@Table(name = "schema_presets")
@Getter
@NoArgsConstructor
public class SchemaPreset {

  /**
   * Short identifier for the preset (e.g. {@code "plumber"}, {@code "locksmith"}). Used as the
   * primary key and as the {@code preset_key} passed to {@link
   * com.teamhub.tenancy.InternalTenantService#provision(String, String, String)}.
   */
  @Id
  @Column(name = "preset_key", nullable = false, updatable = false)
  private String presetKey;

  /** Human-readable name used as the {@link AppointmentSchema#getName()} on first publish. */
  @Column(name = "schema_name", nullable = false)
  private String schemaName;

  /** Ordered list of field descriptors stored as JSONB. */
  @Column(name = "field_definitions", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private List<FieldDefinition> fieldDefinitions;
}
