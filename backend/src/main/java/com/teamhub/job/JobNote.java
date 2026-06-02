package com.teamhub.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only note attached to a job (PRD §13.2, §13.4; TASK-048).
 *
 * <p>Notes are immutable: no setters, no {@code @PreUpdate} lifecycle callback, and the backing
 * table grants only SELECT + INSERT to {@code teamhub_app}. The {@code @Builder} and
 * {@code @AllArgsConstructor} annotations are provided for construction; Hibernate uses the no-args
 * constructor for loading.
 */
@Entity
@Table(name = "job_notes")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobNote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Column(nullable = false, updatable = false)
  private String body;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
