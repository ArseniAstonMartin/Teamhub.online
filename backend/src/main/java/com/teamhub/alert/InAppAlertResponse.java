package com.teamhub.alert;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of an {@link InAppAlert} returned by the REST API (PRD §28, TASK-118).
 *
 * @param id alert UUID
 * @param jobId job UUID that triggered the alert (may be {@code null})
 * @param kind alert kind string, e.g. {@code "JOB_NOT_HANDLED"}
 * @param createdAt timestamp when the alert was created
 */
public record InAppAlertResponse(UUID id, UUID jobId, String kind, Instant createdAt) {

  public static InAppAlertResponse from(InAppAlert a) {
    return new InAppAlertResponse(a.getId(), a.getJobId(), a.getKind(), a.getCreatedAt());
  }
}
