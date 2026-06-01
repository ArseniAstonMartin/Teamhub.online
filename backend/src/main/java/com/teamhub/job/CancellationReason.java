package com.teamhub.job;

/**
 * Reason values used when a job is cancelled at {@link JobStatus#CANCELED} (PRD §9.1).
 *
 * <p>Maps to the nullable {@code cancellation_reason} text column.
 */
public enum CancellationReason {
  /** Customer declined the service due to price. */
  TOO_EXPENSIVE,

  /** Technician arrived later than the agreed window. */
  ARRIVED_LATE,

  /** Any other cancellation reason. */
  OTHER
}
