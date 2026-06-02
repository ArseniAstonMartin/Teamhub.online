package com.teamhub.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for append-only job notes (PRD §13.2, §13.4; TASK-048).
 *
 * <p>No delete or update operations are exposed; the backing table enforces immutability at the DB
 * level (GRANT SELECT, INSERT only).
 */
public interface JobNoteRepository extends JpaRepository<JobNote, UUID> {

  /**
   * Returns all notes for the given job ordered by creation timestamp descending (newest first).
   *
   * @param jobId the job whose notes to retrieve
   * @return immutable-ordered list of notes
   */
  List<JobNote> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}
