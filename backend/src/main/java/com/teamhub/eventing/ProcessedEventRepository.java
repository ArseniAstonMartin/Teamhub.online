package com.teamhub.eventing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the de-dup guard table {@link ProcessedEvent} (PRD §22.5.6).
 *
 * <p>All consumer groups use this repository to mark an event as handled. The idiomatic consumer
 * usage is a native INSERT ... ON CONFLICT DO NOTHING (to detect duplicate delivery without loading
 * a full row); this repository is provided for programmatic access from Java code.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

  long countByTenantId(UUID tenantId);
}
