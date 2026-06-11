package com.teamhub.storage.dto;

import com.teamhub.storage.JobPhoto;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned after a photo upload is confirmed (PRD §14.2; TASK-049).
 *
 * @param id unique photo record identifier.
 * @param jobId the job this photo belongs to.
 * @param objectKey storage object key for the photo.
 * @param contentType MIME type of the photo.
 * @param sizeBytes byte size of the photo.
 * @param createdAt timestamp when the record was persisted.
 * @param downloadUrl short-lived pre-signed GET URL; {@code null} on upload confirm responses.
 */
public record JobPhotoResponse(
    UUID id,
    UUID jobId,
    String objectKey,
    String contentType,
    long sizeBytes,
    Instant createdAt,
    String downloadUrl) {

  /** Builds a {@link JobPhotoResponse} from a {@link JobPhoto} entity without a download URL. */
  public static JobPhotoResponse from(JobPhoto photo) {
    return from(photo, null);
  }

  /**
   * Builds a {@link JobPhotoResponse} from a {@link JobPhoto} entity with an optional download URL.
   */
  public static JobPhotoResponse from(JobPhoto photo, String downloadUrl) {
    return new JobPhotoResponse(
        photo.getId(),
        photo.getJobId(),
        photo.getObjectKey(),
        photo.getContentType(),
        photo.getSizeBytes(),
        photo.getCreatedAt(),
        downloadUrl);
  }
}
