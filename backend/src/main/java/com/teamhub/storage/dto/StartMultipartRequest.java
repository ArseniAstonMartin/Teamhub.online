package com.teamhub.storage.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for starting a multipart upload session (PRD §14.4; TASK-050).
 *
 * @param contentType MIME type of the file to upload; must be in the allowed list.
 * @param fileExtension file extension without leading dot (e.g. {@code "jpg"}).
 */
public record StartMultipartRequest(@NotBlank String contentType, @NotBlank String fileExtension) {}
