package com.teamhub.eventing;

/**
 * Plug-in interface for handling a due {@link DelayedJob} (PRD §22.5.4).
 *
 * <p>Implementations must be registered as Spring beans so that {@link DelayedJobDispatcher} can
 * collect them into a {@code kind → handler} registry at startup. Each implementation must return a
 * unique, stable {@link #kind()} string that matches the {@code kind} column stored in the {@code
 * delayed_jobs} table.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Component
 * public class EscalateUnclaimedJobHandler implements DelayedJobHandler {
 *     @Override public String kind() { return "escalate_unclaimed_job"; }
 *     @Override public void handle(DelayedJob job) { ... }
 * }
 * }</pre>
 *
 * <p>Handlers must be idempotent: the dispatcher may invoke the handler more than once for the same
 * job in rare crash-recovery scenarios.
 */
public interface DelayedJobHandler {

  /**
   * Returns the unique job kind this handler processes. Must match the {@code kind} value stored in
   * the {@code delayed_jobs} table row.
   */
  String kind();

  /**
   * Executes the business logic for the given due job.
   *
   * <p>If this method throws any {@link Exception}, the dispatcher will increment the job's {@code
   * attempt_count} and either re-schedule the job for an immediate retry (if {@code attempt_count <
   * 5}) or mark it {@code FAILED} (if {@code attempt_count >= 5}).
   *
   * @param job the due job; status is {@code RUNNING} at time of invocation
   */
  void handle(DelayedJob job);
}
