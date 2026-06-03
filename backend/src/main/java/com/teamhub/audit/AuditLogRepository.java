package com.teamhub.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only repository for {@link AuditLog} rows (PRD §19).
 *
 * <p>Inserts must go through {@link AuditLogService#record} which uses the {@code app_audit_writer}
 * role. Direct {@code save()} calls from application code are forbidden — the Spring-managed data
 * source runs as {@code teamhub_app} which has SELECT only on {@code audit_log}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  /**
   * Returns all audit log rows for the given tenant (TASK-071, PRD §24.5).
   *
   * <p>Used by the CCPA data-export feature to bundle {@code audit_log.json}.
   */
  List<AuditLog> findAllByTenantId(UUID tenantId);
}
