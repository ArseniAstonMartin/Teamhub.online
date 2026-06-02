package com.teamhub.storage;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.PhotoUploaded;
import com.teamhub.storage.dto.ConfirmUploadRequest;
import com.teamhub.storage.dto.JobPhotoResponse;
import com.teamhub.storage.dto.UploadUrlResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Handles pre-signed URL generation and photo upload confirmation (PRD §14.2, §14.3; TASK-049).
 *
 * <p>Allowed content types (PRD §14.3): {@code image/jpeg}, {@code image/png}, {@code image/heic},
 * {@code image/webp}.
 *
 * <p>Maximum confirmed photo size: 20 MB.
 *
 * <p>Object key format: {@code <tenantId>/jobs/<jobId>/<uuid>.<ext>}.
 * <!-- TO-DO (v1.1): EXIF stripping — strip EXIF metadata from uploaded images via a background
 *      job before serving; until then, processed copies are served as-is. Human review required
 *      before implementation. -->
 */
@Service
public class PhotoUploadService {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/heic", "image/webp");

  private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

  private final S3Presigner s3Presigner;

  private final ObjectStorageProperties storageProperties;

  private final JobPhotoRepository jobPhotoRepository;

  private final OutboxPublisher outboxPublisher;

  public PhotoUploadService(
      S3Presigner s3Presigner,
      ObjectStorageProperties storageProperties,
      JobPhotoRepository jobPhotoRepository,
      OutboxPublisher outboxPublisher) {
    this.s3Presigner = s3Presigner;
    this.storageProperties = storageProperties;
    this.jobPhotoRepository = jobPhotoRepository;
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Generates a pre-signed PUT URL for uploading a photo (PRD §14.2).
   *
   * @param tenantId tenant context
   * @param jobId the job the photo belongs to
   * @param userId the uploading user
   * @param contentType MIME type of the photo; must be in the allow-list
   * @param fileExtension file extension without leading dot (e.g. {@code "jpg"})
   * @return an {@link UploadUrlResponse} containing the pre-signed URL, object key, and expiry
   * @throws UnsupportedMimeTypeException if {@code contentType} is not allowed
   */
  public UploadUrlResponse generateUploadUrl(
      UUID tenantId, UUID jobId, UUID userId, String contentType, String fileExtension) {

    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new UnsupportedMimeTypeException(contentType);
    }

    String objectKey =
        "%s/jobs/%s/%s.%s".formatted(tenantId, jobId, UUID.randomUUID(), fileExtension);

    Duration ttl = Duration.ofMinutes(storageProperties.presignedUrlTtlMinutes());
    Instant expiresAt = Instant.now().plus(ttl);

    PresignedPutObjectRequest presigned =
        s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(
                    r ->
                        r.bucket(storageProperties.bucket())
                            .key(objectKey)
                            .contentType(contentType))
                .build());

    return new UploadUrlResponse(presigned.url().toString(), objectKey, expiresAt);
  }

  /**
   * Confirms a completed photo upload by persisting metadata and publishing a {@code PhotoUploaded}
   * event in the same transaction (PRD §14.2; TASK-049).
   *
   * @param tenantId tenant context
   * @param jobId the job the photo belongs to
   * @param userId the uploading user
   * @param request the confirmation request containing object key, size, and optional EXIF data
   * @return a {@link JobPhotoResponse} for the saved record
   * @throws PhotoTooLargeException if {@code sizeBytes} exceeds 20 MB
   * @throws IllegalArgumentException if the object key does not belong to this tenant/job
   */
  @Transactional
  public JobPhotoResponse confirmUpload(
      UUID tenantId, UUID jobId, UUID userId, ConfirmUploadRequest request) {

    if (request.sizeBytes() > MAX_SIZE_BYTES) {
      throw new PhotoTooLargeException(request.sizeBytes());
    }

    String expectedPrefix = "%s/jobs/%s/".formatted(tenantId, jobId);
    if (!request.objectKey().startsWith(expectedPrefix)) {
      throw new IllegalArgumentException(
          "Object key '%s' does not belong to tenant %s / job %s."
              .formatted(request.objectKey(), tenantId, jobId));
    }

    Instant takenAt = request.takenAt() != null ? request.takenAt().toInstant() : null;

    JobPhoto photo =
        JobPhoto.builder()
            .jobId(jobId)
            .tenantId(tenantId)
            .uploadedBy(userId)
            .objectKey(request.objectKey())
            .contentType(request.contentType())
            .sizeBytes(request.sizeBytes())
            .width(request.width())
            .height(request.height())
            .takenAt(takenAt)
            .build();

    jobPhotoRepository.save(photo);

    outboxPublisher.publish(
        "PhotoUploaded",
        "Job",
        jobId,
        new PhotoUploaded(UUID.randomUUID(), tenantId, jobId, photo.getId(), null, userId));

    return JobPhotoResponse.from(photo);
  }
}
