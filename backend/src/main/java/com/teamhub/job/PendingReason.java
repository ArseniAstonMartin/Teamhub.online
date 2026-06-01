package com.teamhub.job;

/**
 * Reason values used when a job is paused at {@link JobStatus#PENDING} (PRD §9.1).
 *
 * <p>Maps to the nullable {@code pending_reason} text column.
 */
public enum PendingReason {
  /** Awaiting replacement or additional parts. */
  WAITING_FOR_PARTS,

  /** Job needs to be rescheduled to a different time slot. */
  RESCHEDULING_NEEDED
}
