package com.teamhub.job;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data JPA repository for {@link Job} (PRD §9, §22, TASK-042, TASK-044). */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

  /**
   * Returns an active (non-archived) job for the given tenant and id.
   *
   * <p>Used by update/archive/hard-delete paths to ensure cross-tenant isolation even when the test
   * DataSource connects as the postgres super-user (bypasses RLS).
   */
  Optional<Job> findByIdAndTenantIdAndArchivedAtIsNull(UUID id, UUID tenantId);
}
