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
import lombok.Setter;

/**
 * Tracks an in-progress or terminal S3 multipart upload session for a job (PRD §14.4, TASK-050).
 *
 * <p>Status transitions: {@code IN_PROGRESS → COMPLETED} (via {@code completeUpload}) or {@code
 * IN_PROGRESS → ABORTED} (via {@code abortUpload} or the 24-hour cleanup job).
 */
@Entity
@Table(name = "multipart_uploads")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUpload {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "initiated_by", nullable = false, updatable = false)
  private UUID initiatedBy;

  @Column(name = "s3_upload_id", nullable = false, updatable = false)
  private String s3UploadId;

  @Column(name = "object_key", nullable = false, updatable = false)
  private String objectKey;

  @Setter
  @Column(name = "status", nullable = false)
  private String status;

  @Setter
  @Column(name = "part_count", nullable = false)
  private int partCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Setter
  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();
}
