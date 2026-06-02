package com.teamhub.storage.dto;

import java.time.Instant;

/**
 * Response containing a pre-signed URL for uploading one part of a multipart upload (PRD §14.4;
 * TASK-050).
 *
 * @param uploadUrl pre-signed PUT URL for this part.
 * @param partNumber the part number this URL targets (1-based).
 * @param expiresAt UTC instant when the pre-signed URL expires.
 */
public record PartUploadUrlResponse(String uploadUrl, int partNumber, Instant expiresAt) {}
