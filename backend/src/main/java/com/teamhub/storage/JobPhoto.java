package com.teamhub.storage;

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
 * Confirmed photo upload record attached to a job (PRD §14.2, §14.3; TASK-049).
 *
 * <p>Records are immutable after creation: no setters, no {@code @PreUpdate}. The backing table
 * grants only SELECT + INSERT to {@code teamhub_app}.
 */
@Entity
@Table(name = "job_photos")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPhoto {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "uploaded_by", nullable = false, updatable = false)
  private UUID uploadedBy;

  @Column(name = "object_key", nullable = false, updatable = false)
  private String objectKey;

  @Column(name = "content_type", nullable = false, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  /** EXIF image width in pixels; null when not provided by the client. */
  @Column(updatable = false)
  private Integer width;

  /** EXIF image height in pixels; null when not provided by the client. */
  @Column(updatable = false)
  private Integer height;

  /** EXIF capture timestamp; null when not embedded or not provided by the client. */
  @Column(name = "taken_at", updatable = false)
  private Instant takenAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
