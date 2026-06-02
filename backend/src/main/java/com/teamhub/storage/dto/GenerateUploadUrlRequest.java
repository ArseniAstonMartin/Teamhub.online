package com.teamhub.storage.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/jobs/{id}/photo-upload-url} (PRD §14.2; TASK-049).
 *
 * @param contentType MIME type of the photo (must be one of the allowed image types).
 * @param fileExtension file extension without leading dot (e.g. {@code "jpg"}).
 */
public record GenerateUploadUrlRequest(
    @NotBlank String contentType, @NotBlank String fileExtension) {}
