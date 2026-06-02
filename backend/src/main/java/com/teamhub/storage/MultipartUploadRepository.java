package com.teamhub.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link MultipartUpload} (TASK-050). */
public interface MultipartUploadRepository extends JpaRepository<MultipartUpload, UUID> {

  Optional<MultipartUpload> findByIdAndTenantId(UUID id, UUID tenantId);

  @Query("SELECT m FROM MultipartUpload m WHERE m.status = 'IN_PROGRESS' AND m.createdAt < :cutoff")
  List<MultipartUpload> findStaleUploads(@Param("cutoff") Instant cutoff);
}
