package com.teamhub.storage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link JobPhoto} (TASK-049). */
public interface JobPhotoRepository extends JpaRepository<JobPhoto, UUID> {

  /** Returns all photos for a job within a tenant, newest first (PRD §14.2; TASK-123). */
  List<JobPhoto> findByJobIdAndTenantIdOrderByCreatedAtDesc(UUID jobId, UUID tenantId);
}
