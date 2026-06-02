package com.teamhub.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A trigger → action automation rule (PRD §16.2; TASK-051).
 *
 * <p>JSONB columns ({@code trigger_fields}, {@code match_rules}, {@code actions}) are mapped via
 * Hibernate 6's built-in JSON type using the application's registered ObjectMapper. Soft-delete is
 * modelled as {@code archived_at IS NULL} for active rules; {@link AutomationRuleService#archive}
 * sets the timestamp without hard-deleting the row.
 */
@Entity
@Table(name = "automation_rules")
@Getter
@Setter
@NoArgsConstructor
public class AutomationRule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  /**
   * Ordered list of field keys that serve as rule triggers (e.g. {@code ["service_type","make"]}).
   */
  @Column(name = "trigger_fields", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private List<String> triggerFields;

  /**
   * Expected values for trigger fields that must ALL match for this rule to fire (PRD §16.2).
   * Example: {@code {"service_type":"All Keys Lost","make":"Mercedes-Benz"}}.
   */
  @Column(name = "match_rules", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> matchRules;

  /**
   * Field values to auto-fill when the rule matches (PRD §16.2). Example: {@code
   * {"equipment":["Autel Programmer"],"duration_min":30}}.
   */
  @Column(name = "actions", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> actions;

  /** Whether this rule participates in the evaluation loop (PRD §16.3). */
  @Column(nullable = false)
  private boolean active = true;

  /** Evaluation order: lower value = evaluated first (PRD §16.3). Default 100. */
  @Column(nullable = false)
  private int priority = 100;

  /** Non-null when the rule has been soft-deleted (archived). */
  @Column(name = "archived_at")
  private Instant archivedAt;

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
