package com.teamhub.job.dto;

import com.teamhub.job.JobStatusHistory;
import java.time.Instant;

/**
 * Response DTO for a single job status transition (PRD §9; TASK-124).
 *
 * <p>Exposes the audit fields required by the Job Detail status timeline without internal IDs.
 */
public record JobStatusHistoryResponse(
    String fromStatus,
    String toStatus,
    String actorRole,
    String transitionNote,
    Instant createdAt) {

  /**
   * Builds a {@link JobStatusHistoryResponse} from the given {@link JobStatusHistory} entity.
   *
   * @param row the persisted history row
   * @return the response DTO
   */
  public static JobStatusHistoryResponse from(JobStatusHistory row) {
    return new JobStatusHistoryResponse(
        row.getFromStatus(),
        row.getToStatus(),
        row.getActorRole(),
        row.getTransitionNote(),
        row.getCreatedAt());
  }
}
