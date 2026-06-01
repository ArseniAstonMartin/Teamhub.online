package com.teamhub.eventing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delayed Job Dispatcher: every 5 s it dequeues due entries from the {@code delayed_jobs} Redis
 * sorted set and runs the appropriate {@link DelayedJobHandler} (PRD §22.5.4).
 *
 * <h3>Sorted-set contract</h3>
 *
 * <p>When a job is scheduled ({@link #schedule}), two writes happen atomically from the caller's
 * perspective:
 *
 * <ol>
 *   <li>A {@code delayed_jobs} Postgres row is inserted (status = {@code PENDING}).
 *   <li>The job ID is added to the {@value #SORTED_SET_KEY} Redis sorted set with score = {@code
 *       run_at} epoch-millis.
 * </ol>
 *
 * <p>On each dispatch tick, {@code ZRANGEBYSCORE delayed_jobs 0 <now_ms> LIMIT 0 100} retrieves up
 * to {@value #BATCH_SIZE} due job IDs. Each ID is {@code ZREM}-ed immediately (preventing
 * double-dispatch even across restarts) and then processed.
 *
 * <h3>Pre-run check</h3>
 *
 * <p>After {@code ZREM}, the dispatcher reads the Postgres row:
 *
 * <ul>
 *   <li>{@code CANCELLED} → mark {@code DONE} and skip.
 *   <li>Row missing → skip (sorted-set entry is already gone; nothing to do).
 *   <li>Not {@code PENDING} (e.g. already {@code DONE}/{@code FAILED}) → skip.
 *   <li>{@code PENDING} → set {@code RUNNING} and invoke handler.
 * </ul>
 *
 * <h3>Retry / failure</h3>
 *
 * <p>On handler error: increment {@code attempt_count}. If {@code attempt_count >= 5}, set {@code
 * status = 'FAILED'} and emit an ERROR log. Otherwise set {@code status = 'PENDING'} and re-{@code
 * ZADD} for immediate retry.
 *
 * <h3>Single-leader guarantee</h3>
 *
 * <p>{@link SchedulerLock} wraps the dispatch tick so at most one JVM node executes it at a time
 * (backed by the {@code shedlock} Postgres table, migration V11).
 *
 * <h3>RLS / multi-tenant access</h3>
 *
 * <p>The dispatcher does <em>not</em> set {@link com.teamhub.shared.TenantContext}, which causes
 * {@link com.teamhub.shared.TenantAwareDataSource} to issue {@code RESET ROLE} / {@code RESET
 * app.current_tenant_id}. The login user (postgres superuser / BYPASSRLS) then sees all tenants'
 * rows. Production deployments using a non-superuser must grant BYPASSRLS to the dispatcher role.
 */
@Component
public class DelayedJobDispatcher {

  private static final Logger log = LoggerFactory.getLogger(DelayedJobDispatcher.class);

  static final String SORTED_SET_KEY = "delayed_jobs";

  private static final int BATCH_SIZE = 100;

  private static final int MAX_ATTEMPTS = 5;

  private final StringRedisTemplate redis;

  private final JdbcTemplate jdbc;

  private final DelayedJobRepository repository;

  private final Map<String, DelayedJobHandler> handlers;

  public DelayedJobDispatcher(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      DelayedJobRepository repository,
      List<DelayedJobHandler> handlerList) {
    this.redis = redis;
    this.jdbc = jdbc;
    this.repository = repository;
    this.handlers =
        handlerList.stream().collect(Collectors.toUnmodifiableMap(DelayedJobHandler::kind, h -> h));
    if (handlers.isEmpty()) {
      log.warn(
          "DelayedJobDispatcher started with NO registered handlers; all dispatched jobs will be FAILED");
    } else {
      log.info("DelayedJobDispatcher registered handlers: {}", handlers.keySet());
    }
  }

  // =========================================================================
  // Job scheduling
  // =========================================================================

  /**
   * Persists a new {@link DelayedJob} and enqueues it in the Redis sorted set.
   *
   * <p>The caller must have {@link com.teamhub.shared.TenantContext} set so that the Postgres RLS
   * policy allows the INSERT. The sorted-set entry uses {@code runAt} epoch-millis as the score.
   *
   * @return the persisted job (ID is populated after save)
   */
  public DelayedJob schedule(UUID tenantId, String kind, String payload, Instant runAt) {
    DelayedJob job =
        DelayedJob.builder().tenantId(tenantId).kind(kind).payload(payload).runAt(runAt).build();
    job = repository.save(job);
    redis.opsForZSet().add(SORTED_SET_KEY, job.getId().toString(), runAt.toEpochMilli());
    log.debug("Scheduled job id={} kind={} runAt={}", job.getId(), kind, runAt);
    return job;
  }

  // =========================================================================
  // Dispatch tick — every 5 s, single-leader via ShedLock
  // =========================================================================

  /**
   * ShedLock-guarded entry point. Delegates to {@link #dispatchTickInternal()} so that tests can
   * invoke the core logic directly without going through the lock proxy.
   */
  @Scheduled(fixedDelay = 5_000)
  @SchedulerLock(name = "delayed_job_dispatcher", lockAtMostFor = "PT10S")
  public void dispatchTick() {
    dispatchTickInternal();
  }

  /**
   * Core dispatch logic. Exposed with package visibility so integration tests can drive it
   * directly.
   */
  void dispatchTickInternal() {
    long nowMs = Instant.now().toEpochMilli();
    Set<String> dueIds = redis.opsForZSet().rangeByScore(SORTED_SET_KEY, 0, nowMs, 0, BATCH_SIZE);
    if (dueIds == null || dueIds.isEmpty()) {
      return;
    }
    for (String jobIdStr : dueIds) {
      try {
        dispatch(jobIdStr);
      } catch (Exception e) {
        log.error("Unexpected error dispatching job id={}: {}", jobIdStr, e.getMessage(), e);
      }
    }
  }

  // =========================================================================
  // Per-job dispatch
  // =========================================================================

  private void dispatch(String jobIdStr) {
    UUID jobId = UUID.fromString(jobIdStr);

    // Atomically remove from sorted set — prevents any other node from re-picking this job,
    // even if this instance crashes immediately after.
    redis.opsForZSet().remove(SORTED_SET_KEY, jobIdStr);

    // Pre-run check: read current status from Postgres.
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT status, kind FROM delayed_jobs WHERE id = ?", jobId);

    if (rows.isEmpty()) {
      log.warn("delayed_jobs row missing for id={}; ZREM already done, skipping", jobId);
      return;
    }

    String status = (String) rows.get(0).get("status");
    String kind = (String) rows.get(0).get("kind");

    if (DelayedJobStatus.CANCELLED.name().equals(status)) {
      jdbc.update("UPDATE delayed_jobs SET status = 'DONE' WHERE id = ?", jobId);
      log.info("Job {} kind={} was CANCELLED — marked DONE, handler not invoked", jobId, kind);
      return;
    }

    if (!DelayedJobStatus.PENDING.name().equals(status)) {
      log.warn("Job {} kind={} has unexpected status {}; skipping", jobId, kind, status);
      return;
    }

    DelayedJobHandler handler = handlers.get(kind);
    if (handler == null) {
      log.error(
          "No handler registered for kind={} jobId={}; marking FAILED immediately", kind, jobId);
      jdbc.update("UPDATE delayed_jobs SET status = 'FAILED' WHERE id = ?", jobId);
      return;
    }

    // Load full entity so the handler receives a properly-typed object.
    Optional<DelayedJob> jobOpt = repository.findById(jobId);
    if (jobOpt.isEmpty()) {
      log.warn(
          "delayed_jobs row disappeared between status check and load for id={}; skipping", jobId);
      return;
    }

    jdbc.update("UPDATE delayed_jobs SET status = 'RUNNING' WHERE id = ?", jobId);
    try {
      handler.handle(jobOpt.get());
      jdbc.update("UPDATE delayed_jobs SET status = 'DONE' WHERE id = ?", jobId);
      log.debug("Job {} kind={} completed successfully", jobId, kind);
    } catch (Exception e) {
      handleFailure(jobId, jobIdStr, kind, e);
    }
  }

  private void handleFailure(UUID jobId, String jobIdStr, String kind, Exception e) {
    jdbc.update("UPDATE delayed_jobs SET attempt_count = attempt_count + 1 WHERE id = ?", jobId);
    int newCount =
        Optional.ofNullable(
                jdbc.queryForObject(
                    "SELECT attempt_count FROM delayed_jobs WHERE id = ?", Integer.class, jobId))
            .orElse(MAX_ATTEMPTS);

    if (newCount >= MAX_ATTEMPTS) {
      jdbc.update("UPDATE delayed_jobs SET status = 'FAILED' WHERE id = ?", jobId);
      log.error(
          "Job {} kind={} FAILED permanently after {} attempts — last error: {}",
          jobId,
          kind,
          newCount,
          e.getMessage(),
          e);
      // TO-DO: route to Sentry once TASK-Sentry lands
    } else {
      jdbc.update("UPDATE delayed_jobs SET status = 'PENDING' WHERE id = ?", jobId);
      redis.opsForZSet().add(SORTED_SET_KEY, jobIdStr, Instant.now().toEpochMilli());
      log.warn(
          "Job {} kind={} attempt {}/{} failed; re-queued for immediate retry: {}",
          jobId,
          kind,
          newCount,
          MAX_ATTEMPTS,
          e.getMessage());
    }
  }
}
