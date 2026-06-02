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
 */
public record JobPhotoResponse(
    UUID id, UUID jobId, String objectKey, String contentType, long sizeBytes, Instant createdAt) {

  /** Builds a {@link JobPhotoResponse} from a {@link JobPhoto} entity. */
  public static JobPhotoResponse from(JobPhoto photo) {
    return new JobPhotoResponse(
        photo.getId(),
        photo.getJobId(),
        photo.getObjectKey(),
        photo.getContentType(),
        photo.getSizeBytes(),
        photo.getCreatedAt());
  }
}
