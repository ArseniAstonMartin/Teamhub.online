package com.teamhub.job.dto;

import com.teamhub.job.JobNote;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a job note (PRD §13.2; TASK-048).
 *
 * <p>All fields are read-only projections of the persisted {@link JobNote} entity.
 */
public record JobNoteResponse(
    UUID id, UUID jobId, UUID authorUserId, String body, Instant createdAt) {

  /**
   * Builds a {@link JobNoteResponse} from the given {@link JobNote} entity.
   *
   * @param note the persisted note entity
   * @return the response DTO
   */
  public static JobNoteResponse from(JobNote note) {
    return new JobNoteResponse(
        note.getId(), note.getJobId(), note.getAuthorUserId(), note.getBody(), note.getCreatedAt());
  }
}
