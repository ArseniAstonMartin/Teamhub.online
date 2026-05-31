package com.teamhub.schemas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A versioned appointment-schema record (PRD §8.2).
 *
 * <p>Rows are append-only — old versions are never deleted so that historical job records can
 * reference and validate against their {@code schema_version}. {@link SchemaService} enforces
 * single-active semantics: when a new version is published, the previous active row's {@code
 * is_active} is set to {@code false}.
 */
@Entity
@Table(name = "appointment_schemas")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentSchema {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  /** Schema name, e.g. {@code "Automotive Locksmith Job"} (PRD §8.2). */
  @Column(nullable = false)
  private String name;

  /** Monotonically increasing version number within a (tenant_id, name) pair. */
  @Column(nullable = false)
  private int version;

  /**
   * {@code true} for the current active version; {@code false} for all superseded versions.
   * Single-active semantics are enforced by {@link SchemaService#publishNewVersion}.
   */
  @Column(name = "is_active", nullable = false)
  private boolean active;

  /**
   * Ordered list of field descriptors stored as JSONB. Hibernate 6 serialises and deserialises this
   * column using Jackson (the application's registered ObjectMapper).
   */
  @Column(name = "field_definitions", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private List<FieldDefinition> fieldDefinitions;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
