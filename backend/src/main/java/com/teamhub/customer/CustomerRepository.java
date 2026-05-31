package com.teamhub.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link Customer} entities. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

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
}
