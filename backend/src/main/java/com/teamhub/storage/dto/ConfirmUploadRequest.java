package com.teamhub.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/photos} (PRD §14.2; TASK-049).
 *
 * @param objectKey object key returned by the upload-URL endpoint.
 * @param contentType MIME type of the uploaded file.
 * @param sizeBytes actual byte size of the uploaded file; validated ≤ 20 MB in the service.
 * @param width image width in pixels (optional, from EXIF).
 * @param height image height in pixels (optional, from EXIF).
 * @param takenAt EXIF capture timestamp (optional).
 */
public record ConfirmUploadRequest(
    @NotBlank String objectKey,
    @NotBlank String contentType,
    @Positive long sizeBytes,
    Integer width,
    Integer height,
    OffsetDateTime takenAt) {}
