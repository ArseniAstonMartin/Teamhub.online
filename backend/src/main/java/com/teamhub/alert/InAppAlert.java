package com.teamhub.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-app alert banner persisted for an Admin user (PRD §28, TASK-118).
 *
 * <p>Created by {@link com.teamhub.escalation.EscalateUnclaimedJobHandler} when a job remains in
 * {@code SUBMITTED} status after the escalation window. Dismissed via {@code POST
 * /api/v1/alerts/{id}/dismiss}. Access is enforced at the service layer by matching {@code userId}
 * to the authenticated principal — no RLS on this table.
 */
@Entity
@Table(name = "in_app_alerts")
@Getter
@Setter
@NoArgsConstructor
public class InAppAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  /** Nullable — future alert kinds may not reference a specific job. */
  @Column(name = "job_id")
  private UUID jobId;

  @Column(nullable = false)
  private String kind = "JOB_NOT_HANDLED";

  @Column(nullable = false)
  private boolean dismissed = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
