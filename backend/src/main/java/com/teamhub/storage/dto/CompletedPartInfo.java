package com.teamhub.storage.dto;

/**
 * Identifies a single completed part with its ETag returned by S3 (PRD §14.4; TASK-050).
 *
 * @param partNumber 1-based part number.
 * @param eTag ETag value returned by S3 when the part was uploaded.
 */
public record CompletedPartInfo(int partNumber, String eTag) {}
