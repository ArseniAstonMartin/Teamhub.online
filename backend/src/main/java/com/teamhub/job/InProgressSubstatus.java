package com.teamhub.job;

/**
 * Sub-status values used when a job is {@link JobStatus#IN_PROGRESS} (PRD §9.1).
 *
 * <p>Maps to the nullable {@code in_progress_substatus} text column.
 */
public enum InProgressSubstatus {
  /** Technician is travelling to the job site. */
  ON_THE_WAY,

  /** Technician has arrived and is actively working. */
  WORKING
}
