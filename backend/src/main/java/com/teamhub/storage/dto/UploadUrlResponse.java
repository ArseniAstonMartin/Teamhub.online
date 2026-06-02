package com.teamhub.storage.dto;

import java.time.Instant;

/**
 * Response for the pre-signed URL generation endpoint (PRD §14.2; TASK-049).
 *
 * @param uploadUrl pre-signed PUT URL valid for {@code presigned-url-ttl-minutes} minutes.
 * @param objectKey the object key that must be passed to the confirm endpoint after upload.
 * @param expiresAt the instant at which the pre-signed URL expires.
 */
public record UploadUrlResponse(String uploadUrl, String objectKey, Instant expiresAt) {}
