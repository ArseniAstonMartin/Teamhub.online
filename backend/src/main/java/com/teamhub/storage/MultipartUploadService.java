package com.teamhub.storage;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.eventing.payloads.PhotoUploaded;
import com.teamhub.storage.dto.CompleteMultipartRequest;
import com.teamhub.storage.dto.JobPhotoResponse;
import com.teamhub.storage.dto.PartUploadUrlResponse;
import com.teamhub.storage.dto.StartMultipartRequest;
import com.teamhub.storage.dto.StartMultipartResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * Manages S3 multipart upload sessions for large photo uploads (PRD §14.4, TASK-050).
 *
 * <p>Allowed content types (PRD §14.3): {@code image/jpeg}, {@code image/png}, {@code image/heic},
 * {@code image/webp}.
 *
 * <p>Object key format: {@code <tenantId>/jobs/<jobId>/<uuid>.<ext>}.
 *
 * <p>The 24-hour cleanup job aborts any {@code IN_PROGRESS} upload older than 24 hours.
 * <!-- TO-DO: also configure S3 bucket lifecycle policy for incomplete multipart uploads -->
 */
@Service
public class MultipartUploadService {

  private static final Logger log = LoggerFactory.getLogger(MultipartUploadService.class);

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/heic", "image/webp");

  private static final Duration PART_URL_TTL = Duration.ofMinutes(15);

  private static final Duration STALE_CUTOFF = Duration.ofHours(24);

  private final S3Client s3Client;

  private final S3Presigner s3Presigner;

  private final ObjectStorageProperties storageProperties;

  private final MultipartUploadRepository multipartUploadRepository;

  private final JobPhotoRepository jobPhotoRepository;

  private final OutboxPublisher outboxPublisher;

  public MultipartUploadService(
      S3Client s3Client,
      S3Presigner s3Presigner,
      ObjectStorageProperties storageProperties,
      MultipartUploadRepository multipartUploadRepository,
      JobPhotoRepository jobPhotoRepository,
      OutboxPublisher outboxPublisher) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.storageProperties = storageProperties;
    this.multipartUploadRepository = multipartUploadRepository;
    this.jobPhotoRepository = jobPhotoRepository;
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Initiates a new S3 multipart upload session (PRD §14.4).
   *
   * @param tenantId tenant context
   * @param jobId the job the photo belongs to
   * @param userId the uploading user
   * @param request contains {@code contentType} and {@code fileExtension}
   * @return a {@link StartMultipartResponse} with the server-side {@code uploadId} and {@code
   *     objectKey}
   * @throws UnsupportedMimeTypeException if {@code contentType} is not in the allow-list
   */
  @Transactional
  public StartMultipartResponse startUpload(
      UUID tenantId, UUID jobId, UUID userId, StartMultipartRequest request) {

    if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
      throw new UnsupportedMimeTypeException(request.contentType());
    }

    String objectKey =
        "%s/jobs/%s/%s.%s".formatted(tenantId, jobId, UUID.randomUUID(), request.fileExtension());

    String s3UploadId =
        s3Client
            .createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(objectKey)
                    .contentType(request.contentType())
                    .build())
            .uploadId();

    MultipartUpload upload =
        MultipartUpload.builder()
            .jobId(jobId)
            .tenantId(tenantId)
            .initiatedBy(userId)
            .s3UploadId(s3UploadId)
            .objectKey(objectKey)
            .status("IN_PROGRESS")
            .partCount(0)
            .build();

    upload = multipartUploadRepository.save(upload);

    return new StartMultipartResponse(upload.getId(), objectKey);
  }

  /**
   * Generates a pre-signed URL for uploading a single part (PRD §14.4).
   *
   * @param tenantId tenant context
   * @param uploadId the server-side multipart upload session ID
   * @param partNumber 1-based part number
   * @return a {@link PartUploadUrlResponse} with the pre-signed URL and expiry
   * @throws MultipartUploadNotFoundException if no upload exists for the given ID + tenant
   * @throws MultipartUploadAlreadyCompletedException if the upload is not {@code IN_PROGRESS}
   */
  public PartUploadUrlResponse getPartUploadUrl(UUID tenantId, UUID uploadId, int partNumber) {
    MultipartUpload upload = loadAndValidateInProgress(tenantId, uploadId);

    Instant expiresAt = Instant.now().plus(PART_URL_TTL);

    PresignedUploadPartRequest presigned =
        s3Presigner.presignUploadPart(
            UploadPartPresignRequest.builder()
                .signatureDuration(PART_URL_TTL)
                .uploadPartRequest(
                    r ->
                        r.bucket(storageProperties.bucket())
                            .key(upload.getObjectKey())
                            .uploadId(upload.getS3UploadId())
                            .partNumber(partNumber))
                .build());

    return new PartUploadUrlResponse(presigned.url().toString(), partNumber, expiresAt);
  }

  /**
   * Completes the multipart upload: assembles all parts in S3, persists a {@link JobPhoto} record,
   * and publishes a {@code PhotoUploaded} event (PRD §14.4).
   *
   * @param tenantId tenant context
   * @param jobId the job the photo belongs to
   * @param userId the completing user
   * @param uploadId the server-side upload session ID
   * @param request completed parts with eTags
   * @return a {@link JobPhotoResponse} for the newly created photo record
   * @throws MultipartUploadNotFoundException if no upload exists for the given ID + tenant
   * @throws MultipartUploadAlreadyCompletedException if the upload is not {@code IN_PROGRESS}
   */
  @Transactional
  public JobPhotoResponse completeUpload(
      UUID tenantId, UUID jobId, UUID userId, UUID uploadId, CompleteMultipartRequest request) {

    MultipartUpload upload = loadAndValidateInProgress(tenantId, uploadId);

    List<CompletedPart> completedParts =
        request.parts().stream()
            .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
            .collect(Collectors.toList());

    s3Client.completeMultipartUpload(
        CompleteMultipartUploadRequest.builder()
            .bucket(storageProperties.bucket())
            .key(upload.getObjectKey())
            .uploadId(upload.getS3UploadId())
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build());

    upload.setStatus("COMPLETED");
    upload.setPartCount(request.parts().size());
    upload.setUpdatedAt(Instant.now());
    multipartUploadRepository.save(upload);

    JobPhoto photo =
        JobPhoto.builder()
            .jobId(jobId)
            .tenantId(tenantId)
            .uploadedBy(userId)
            .objectKey(upload.getObjectKey())
            .contentType(resolveContentType(upload.getObjectKey()))
            .sizeBytes(0L)
            .build();
    jobPhotoRepository.save(photo);

    outboxPublisher.publish(
        "PhotoUploaded",
        "Job",
        jobId,
        new PhotoUploaded(UUID.randomUUID(), tenantId, jobId, photo.getId(), null, userId));

    return JobPhotoResponse.from(photo);
  }

  /**
   * Aborts a multipart upload session, cancelling in-progress part uploads in S3 (PRD §14.4).
   *
   * @param tenantId tenant context
   * @param uploadId the server-side upload session ID
   * @throws MultipartUploadNotFoundException if no upload exists for the given ID + tenant
   * @throws MultipartUploadAlreadyCompletedException if the upload is not {@code IN_PROGRESS}
   */
  @Transactional
  public void abortUpload(UUID tenantId, UUID uploadId) {
    MultipartUpload upload = loadAndValidateInProgress(tenantId, uploadId);

    s3Client.abortMultipartUpload(
        AbortMultipartUploadRequest.builder()
            .bucket(storageProperties.bucket())
            .key(upload.getObjectKey())
            .uploadId(upload.getS3UploadId())
            .build());

    upload.setStatus("ABORTED");
    upload.setUpdatedAt(Instant.now());
    multipartUploadRepository.save(upload);
  }

  // =========================================================================
  // 24-hour lifecycle cleanup (PRD §14.4, TASK-050)
  // =========================================================================

  /**
   * Scheduled cleanup job: aborts any {@code IN_PROGRESS} multipart upload older than 24 hours (PRD
   * §14.4). Runs once per hour, single-leader via ShedLock.
   *
   * <p>
   * <!-- TO-DO: also configure S3 bucket lifecycle policy for incomplete multipart uploads -->
   */
  @Scheduled(fixedDelay = 3_600_000)
  @SchedulerLock(name = "multipart_upload_cleanup", lockAtMostFor = "PT5M")
  public void cleanupStaleUploads() {
    Instant cutoff = Instant.now().minus(STALE_CUTOFF);
    List<MultipartUpload> stale = multipartUploadRepository.findStaleUploads(cutoff);
    if (stale.isEmpty()) {
      return;
    }
    log.info("Aborting {} stale multipart upload(s) older than 24h", stale.size());
    for (MultipartUpload upload : stale) {
      abortStale(upload);
    }
  }

  /**
   * Aborts a single stale upload without tenant-context validation. Exposed package-private for
   * direct unit-test invocation.
   */
  void abortStale(MultipartUpload upload) {
    try {
      s3Client.abortMultipartUpload(
          AbortMultipartUploadRequest.builder()
              .bucket(storageProperties.bucket())
              .key(upload.getObjectKey())
              .uploadId(upload.getS3UploadId())
              .build());
    } catch (Exception e) {
      log.warn(
          "S3 abortMultipartUpload failed for upload id={} s3UploadId={}: {}",
          upload.getId(),
          upload.getS3UploadId(),
          e.getMessage());
    }
    upload.setStatus("ABORTED");
    upload.setUpdatedAt(Instant.now());
    multipartUploadRepository.save(upload);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private MultipartUpload loadAndValidateInProgress(UUID tenantId, UUID uploadId) {
    MultipartUpload upload =
        multipartUploadRepository
            .findByIdAndTenantId(uploadId, tenantId)
            .orElseThrow(() -> new MultipartUploadNotFoundException(uploadId));

    if (!"IN_PROGRESS".equals(upload.getStatus())) {
      throw new MultipartUploadAlreadyCompletedException(uploadId);
    }

    return upload;
  }

  private String resolveContentType(String objectKey) {
    String lower = objectKey.toLowerCase();
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".heic")) {
      return "image/heic";
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return "application/octet-stream";
  }
}
