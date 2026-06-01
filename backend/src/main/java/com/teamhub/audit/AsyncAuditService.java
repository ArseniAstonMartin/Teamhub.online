package com.teamhub.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Thin async wrapper around {@link AuditLogService} (TASK-028, PRD §19.2).
 *
 * <p>All methods in this class are annotated with {@code @Async} so that the Spring proxy
 * intercepts the call and submits it to the task executor. Callers (e.g. {@code AuthService})
 * invoke {@code recordAsync(entry)} and return immediately — audit writes never block the auth
 * response.
 *
 * <p>Uncaught exceptions thrown by the async task are handled by the {@code
 * AsyncUncaughtExceptionHandler} configured in {@link com.teamhub.config.AsyncConfig}: they are
 * logged at ERROR level and do not propagate to the caller's thread.
 *
 * <p>This class is a separate Spring bean (not a self-call on {@code AuditLogService}) because
 * Spring AOP proxies only intercept calls that cross bean boundaries; a {@code @Async} annotation
 * on a method called from within the same bean would be silently ignored.
 */
@Service
public class AsyncAuditService {

  private final AuditLogService auditLogService;

  public AsyncAuditService(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  /**
   * Records an audit entry asynchronously. The method returns immediately; the actual DB write
   * happens in a background thread managed by Spring's task executor.
   *
   * <p>If {@link AuditLogService#record} throws, the exception is swallowed by the async executor
   * and handled by the configured {@link
   * org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler}.
   *
   * @param entry the audit log entry to record; must have {@code tenantId} and {@code action} set
   */
  @Async
  public void recordAsync(AuditLog entry) {
    auditLogService.record(entry);
  }
}
