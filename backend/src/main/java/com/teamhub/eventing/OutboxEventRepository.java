package com.teamhub.eventing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link OutboxEvent} (PRD §22.5.3).
 *
 * <p>The relay uses {@link #findUnprocessed()} to discover events pending delivery to Redis
 * Streams. Producers insert rows via {@link #save(Object)}; the relay updates {@code processed_at}
 * via a native UPDATE (outside this repository, to avoid loading all columns unnecessarily).
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Returns all unprocessed events ordered by (processed_at NULLS FIRST, created_at) — matching the
   * relay-scan index {@code idx_outbox_events_relay}.
   */
  @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
  List<OutboxEvent> findUnprocessed();

  /**
   * Returns up to {@code limit} unprocessed events ordered by {@code created_at ASC} — used by the
   * Outbox Relay to bound each polling tick to a fixed batch size (PRD §22.5.3).
   */
  @Query(
      value =
          "SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT"
              + " :limit",
      nativeQuery = true)
  List<OutboxEvent> findUnprocessedBatch(@Param("limit") int limit);

  List<OutboxEvent> findByTenantId(UUID tenantId);
}
