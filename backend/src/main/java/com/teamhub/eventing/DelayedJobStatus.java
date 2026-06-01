package com.teamhub.eventing;

/**
 * Status lifecycle for a {@link DelayedJob} row (PRD §22.5.4).
 *
 * <p>Transitions:
 *
 * <pre>
 *   PENDING → RUNNING → DONE
 *                     ↘ FAILED  (retried up to max attempts)
 *   PENDING → CANCELLED  (e.g. job claimed before escalation fires)
 * </pre>
 */
public enum DelayedJobStatus {

  /** Waiting to be picked up by the dispatcher at {@code run_at}. */
  PENDING,

  /** Dispatcher has claimed the entry and is executing the handler. */
  RUNNING,

  /** Handler completed successfully. */
  DONE,

  /** Handler failed; will be retried or moved to DLQ. */
  FAILED,

  /** Suppressed before execution (e.g. the originating job was claimed). */
  CANCELLED
}
