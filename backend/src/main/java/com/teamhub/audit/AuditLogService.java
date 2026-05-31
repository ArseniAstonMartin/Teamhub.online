package com.teamhub.audit;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit log writer (PRD §19).
 *
 * <h2>Writer-role approach</h2>
 *
 * <p>The {@code audit_log} table is protected by two complementary mechanisms:
 *
 * <ol>
 *   <li><b>Postgres privileges:</b> {@code app_audit_writer} has INSERT only; {@code teamhub_app}
 *       (the normal app role) has SELECT only. No role available to the application can UPDATE or
 *       DELETE rows.
 *   <li><b>Row-Level Security:</b> INSERT is additionally constrained by the {@code
 *       tenant_isolation} policy (same predicate as all other tenant tables) — a writer can only
 *       insert rows whose {@code tenant_id} matches the current {@code app.current_tenant_id} GUC.
 * </ol>
 *
 * <h2>Execution mechanism</h2>
 *
 * <p>{@link #record} runs in a dedicated {@code REQUIRES_NEW} transaction so the audit entry is
 * committed independently of the surrounding business transaction (e.g. a failing job-create does
 * not suppress the audit row). Inside that transaction, a single {@code SET LOCAL ROLE
 * app_audit_writer} switches the active Postgres role for the duration of the transaction — {@code
 * LOCAL} means the switch is automatically reverted when the transaction ends, leaving the pooled
 * connection clean for the next borrow. The {@code app.current_tenant_id} GUC (set by {@code
 * TenantAwareDataSource} on connection borrow) remains in place so the RLS CHECK on the INSERT
 * succeeds.
 *
 * <p>The Postgres login user ({@code postgres}) is granted membership in {@code app_audit_writer}
 * by V8 migration, so {@code SET LOCAL ROLE} is allowed even after the normal connection borrow
 * already did {@code SET ROLE teamhub_app} — PostgreSQL allows switching to any role for which the
 * <em>session</em> user (the original authenticated user) holds membership.
 */
@Service
public class AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private static final String INSERT_SQL =
      """
      INSERT INTO audit_log (
          tenant_id, actor_user_id, action, resource_type, resource_id,
          before_state, after_state, ip_address, user_agent, occurred_at
      ) VALUES (
          ?, ?, ?, ?, ?,
          ?::jsonb, ?::jsonb, CAST(? AS inet), ?, ?
      )
      """;

  private final JdbcTemplate jdbcTemplate;

  public AuditLogService(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  /**
   * Records an audit event in a dedicated transaction using the {@code app_audit_writer} role.
   *
   * <p>Runs in {@code REQUIRES_NEW} so the audit row is committed even if the caller's transaction
   * rolls back (e.g., a failed login attempt must still be logged per PRD §19.2).
   *
   * @param entry the immutable audit log entry built via {@link AuditLog#builder()}
   * @throws DataAccessException if the INSERT fails (e.g. missing GUC → RLS denial)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditLog entry) {
    // Switch to the writer role and explicitly scope the tenant GUC from the entry's tenantId.
    // This is necessary because AuditLogService may be called from contexts that have no
    // TenantContext (e.g. login_failed events occur before any tenant context is established),
    // so the TenantAwareDataSource connection borrow may have already reset the GUC to empty.
    // The SET LOCAL statements are transaction-scoped and auto-revert at transaction end.
    jdbcTemplate.execute("SET LOCAL ROLE app_audit_writer");
    jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + entry.getTenantId() + "'");

    String beforeJson = toJsonString(entry.getBeforeState());
    String afterJson = toJsonString(entry.getAfterState());
    Instant occurredAt = entry.getOccurredAt() != null ? entry.getOccurredAt() : Instant.now();

    jdbcTemplate.update(
        INSERT_SQL,
        entry.getTenantId(),
        entry.getActorUserId(),
        entry.getAction(),
        entry.getResourceType(),
        entry.getResourceId(),
        beforeJson,
        afterJson,
        entry.getIpAddress(),
        entry.getUserAgent(),
        Timestamp.from(occurredAt));

    log.debug(
        "Audit: action={} resourceType={} resourceId={} tenantId={}",
        entry.getAction(),
        entry.getResourceType(),
        entry.getResourceId(),
        entry.getTenantId());
  }

  private static String toJsonString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    // TO-DO (PRD §19): wire Jackson ObjectMapper here for richer before/after state snapshots.
    return value.toString();
  }
}
