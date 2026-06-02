package com.teamhub.storage;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link JobPhoto} (TASK-049). */
public interface JobPhotoRepository extends JpaRepository<JobPhoto, UUID> {}
