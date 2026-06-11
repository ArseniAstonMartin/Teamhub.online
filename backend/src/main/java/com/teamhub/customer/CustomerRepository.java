package com.teamhub.customer;

import com.teamhub.shared.IdNameProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link Customer} entities. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  /** Returns a customer by id and tenantId for tenant-scoped lookups (TASK-071). */
  Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Returns all non-archived customers for the given tenant (TASK-071, PRD §24.5).
   *
   * <p>Used by the CCPA data-export feature to bundle {@code customers.json}.
   */
  List<Customer> findAllByTenantIdAndArchivedAtIsNull(UUID tenantId);

  /**
   * Finds an active (non-archived) customer by phone within the given tenant.
   *
   * <p>Used for upsert-on-phone semantics (PRD §11.3): if a matching customer already exists, the
   * POST endpoint returns its id and merges non-null fields instead of inserting a duplicate.
   */
  Optional<Customer> findByTenantIdAndPhoneAndArchivedAtIsNull(UUID tenantId, String phone);

  /**
   * Paginated search for customers within a tenant.
   *
   * <p>The {@code tenantId} filter is explicit for test safety (the test DataSource connects as the
   * postgres super-user which bypasses RLS). In production the PostgreSQL RLS policy on {@code
   * customers} provides a second isolation layer.
   *
   * @param tenantId the caller's tenant
   * @param q optional free-text search matched case-insensitively against name, phone, and email;
   *     pass {@code null} or empty string to return all matching customers
   * @param includeArchived when {@code true}, archived customers are included in the result set
   * @param pageable pagination and sort parameters
   */
  @Query(
      "SELECT c FROM Customer c "
          + "WHERE c.tenantId = :tenantId "
          + "AND (:includeArchived = true OR c.archivedAt IS NULL) "
          + "AND (:q IS NULL OR :q = '' "
          + "     OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "     OR c.phone LIKE CONCAT('%', :q, '%') "
          + "     OR (c.email IS NOT NULL"
          + "         AND LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))))")
  Page<Customer> search(
      @Param("tenantId") UUID tenantId,
      @Param("q") String q,
      @Param("includeArchived") boolean includeArchived,
      Pageable pageable);

  /**
   * Batch-loads customer display names for job list enrichment (TASK-130, PRD §11).
   *
   * <p>Scoped to {@code tenantId} to preserve tenant isolation in a single IN query.
   */
  @Query(
      "SELECT c.id AS id, c.name AS name FROM Customer c"
          + " WHERE c.tenantId = :tenantId AND c.id IN :ids")
  List<IdNameProjection> findIdAndNameByTenantIdAndIdIn(
      @Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);
}
