package com.teamhub.job;

import com.teamhub.job.dto.JobNoteResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for append-only job notes (PRD §13.2, §13.4; TASK-048).
 *
 * <p>Notes are immutable: {@link #addNote} is the only write operation; there are no update or
 * delete methods. DB-level immutability is enforced by granting only SELECT + INSERT on the {@code
 * job_notes} table (V16 migration).
 */
@Service
@Transactional
public class JobNoteService {

  private final JobRepository jobRepository;

  private final JobNoteRepository jobNoteRepository;

  public JobNoteService(JobRepository jobRepository, JobNoteRepository jobNoteRepository) {
    this.jobRepository = jobRepository;
    this.jobNoteRepository = jobNoteRepository;
  }

  /**
   * Appends a note to an existing job.
   *
   * <p>Verifies that the job exists and belongs to the given tenant; throws {@link
   * JobNotFoundException} (HTTP 404) otherwise.
   *
   * @param tenantId the requesting tenant
   * @param jobId the job to annotate
   * @param authorUserId UUID of the note author
   * @param body note content (1–4 000 chars, validated at controller layer)
   * @return the persisted note as a response DTO
   * @throws JobNotFoundException if the job does not exist or belongs to another tenant
   */
  @Transactional
  public JobNoteResponse addNote(UUID tenantId, UUID jobId, UUID authorUserId, String body) {
    jobRepository
        .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
        .orElseThrow(() -> new JobNotFoundException(jobId));

    JobNote note =
        JobNote.builder()
            .jobId(jobId)
            .tenantId(tenantId)
            .authorUserId(authorUserId)
            .body(body)
            .build();

    return JobNoteResponse.from(jobNoteRepository.save(note));
  }

  /**
   * Returns all notes for the given job in newest-first order.
   *
   * <p>Verifies that the job belongs to the requesting tenant; throws {@link JobNotFoundException}
   * (HTTP 404) if not found.
   *
   * @param tenantId the requesting tenant
   * @param jobId the job whose notes to list
   * @return list of notes ordered by {@code created_at DESC}
   * @throws JobNotFoundException if the job does not exist or belongs to another tenant
   */
  @Transactional(readOnly = true)
  public List<JobNoteResponse> listNotes(UUID tenantId, UUID jobId) {
    jobRepository
        .findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId)
        .orElseThrow(() -> new JobNotFoundException(jobId));

    return jobNoteRepository.findByJobIdOrderByCreatedAtDesc(jobId).stream()
        .map(JobNoteResponse::from)
        .toList();
  }
}
