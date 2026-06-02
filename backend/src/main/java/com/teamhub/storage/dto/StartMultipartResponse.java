package com.teamhub.storage.dto;

import java.util.UUID;

/**
 * Response returned after a multipart upload session is started (PRD §14.4; TASK-050).
 *
 * @param uploadId server-side UUID identifying this upload session.
 * @param objectKey storage object key that will contain the assembled file.
 */
public record StartMultipartResponse(UUID uploadId, String objectKey) {}
