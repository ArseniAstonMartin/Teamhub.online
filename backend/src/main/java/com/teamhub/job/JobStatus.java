package com.teamhub.job;

/**
 * Top-level status of a job in the field-service lifecycle (PRD §9.1).
 *
 * <p>Values map to the {@code status} text column via {@code @Enumerated(EnumType.STRING)}.
 */
public enum JobStatus {
  /** Created by dispatcher, in shared pool, not yet claimed. */
  SUBMITTED,

  /** Claimed by a technician but not yet started. */
  ASSIGNED_TO_TECHNICIAN,

  /** Actively being worked by the assigned technician. */
  IN_PROGRESS,

  /** Paused; see {@link PendingReason} for why. */
  PENDING,

  /** Completed by the technician, awaiting office approval. */
  DONE_PENDING_APPROVAL,

  /** Finalized — closed by the technician (if Close Jobs permission) or a dispatcher/admin. */
  COMPLETED,

  /** Terminal cancelled state. */
  CANCELED
}
