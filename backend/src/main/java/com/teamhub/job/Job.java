package com.teamhub.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Represents a field-service job within a tenant's workspace (PRD §9, §22).
 *
 * <p>The {@code version} column provides optimistic locking — the claim endpoint uses it to ensure
 * atomicity of the "first technician wins" race (PRD §10.2). Hibernate increments the column on
 * every update; a stale-version conflict raises {@link
 * org.springframework.orm.ObjectOptimisticLockingFailureException}.
 *
 * <p>{@code customFields} is validated against the referenced {@link
 * com.teamhub.schemas.AppointmentSchema} by {@link JobCustomFieldValidator} before persisting.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  /** Nullable until the job is claimed by a technician (PRD §10.1). */
  @Column(name = "assigned_technician_id")
  private UUID assignedTechnicianId;

  /**
   * FK to {@code appointment_schemas.id}. Stored as a plain UUID to avoid eagerly loading the full
   * schema entity on every job read.
   */
  @Column(name = "appointment_schema_id")
  private UUID appointmentSchemaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus status;

  /** Set only when {@code status == IN_PROGRESS} (PRD §9.1). */
  @Enumerated(EnumType.STRING)
  @Column(name = "in_progress_substatus")
  private InProgressSubstatus inProgressSubstatus;

  /** Set only when {@code status == PENDING} (PRD §9.1). */
  @Enumerated(EnumType.STRING)
  @Column(name = "pending_reason")
  private PendingReason pendingReason;

  /** Set only when {@code status == CANCELED} (PRD §9.1). */
  @Enumerated(EnumType.STRING)
  @Column(name = "cancellation_reason")
  private CancellationReason cancellationReason;

  /**
   * Tenant-defined custom field values keyed by {@link com.teamhub.schemas.FieldDefinition#key()}.
   * Validated against the active schema version on insert and update (PRD §8.2).
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  private Map<String, Object> customFields;

  /**
   * Service address stored as PostGIS {@code geography(Point,4326)} (same pattern as {@link
   * com.teamhub.customer.Customer}). JTS coordinate order: x = longitude, y = latitude.
   */
  @Column(columnDefinition = "geography(Point,4326)")
  private Point location;

  @Column(name = "scheduled_start")
  private Instant scheduledStart;

  @Column(name = "scheduled_end")
  private Instant scheduledEnd;

  @Column(name = "actual_start")
  private Instant actualStart;

  @Column(name = "actual_end")
  private Instant actualEnd;

  /**
   * Optimistic-locking column (PRD §10.2). Hibernate increments this on every UPDATE; a concurrent
   * modification with a stale version raises {@link
   * org.springframework.orm.ObjectOptimisticLockingFailureException}.
   */
  @Version
  @Column(nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
