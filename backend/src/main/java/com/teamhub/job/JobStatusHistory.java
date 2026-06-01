package com.teamhub.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable audit record for a single job status transition (PRD §9, TASK-043).
 *
 * <p>Rows are written by {@link JobStateMachineService} inside the same transaction as the job
 * status update. No setters are exposed — the entity is write-once.
 */
@Entity
@Table(name = "job_status_history")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  /** Null for the very first transition (no prior status). */
  @Column(name = "from_status", updatable = false)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, updatable = false)
  private String toStatus;

  @Column(name = "actor_user_id", updatable = false)
  private UUID actorUserId;

  @Column(name = "actor_role", updatable = false)
  private String actorRole;

  @Column(name = "transition_note", updatable = false)
  private String transitionNote;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
}
