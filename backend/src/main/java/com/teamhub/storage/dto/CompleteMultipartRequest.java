package com.teamhub.storage.dto;

import java.util.List;

/**
 * Request body for completing a multipart upload (PRD §14.4; TASK-050).
 *
 * @param parts list of completed parts with their eTags, in part-number order.
 */
public record CompleteMultipartRequest(List<CompletedPartInfo> parts) {}
