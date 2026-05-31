package com.teamhub.schemas;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link AppointmentSchema} (PRD §8.2). */
public interface AppointmentSchemaRepository extends JpaRepository<AppointmentSchema, UUID> {

  /**
   * Returns the highest published version number for the given tenant + schema name, or {@link
   * Optional#empty()} if no versions exist yet.
   */
  @Query(
      "SELECT MAX(s.version) FROM AppointmentSchema s "
          + "WHERE s.tenantId = :tenantId AND s.name = :name")
  Optional<Integer> findMaxVersionByTenantIdAndName(
      @Param("tenantId") UUID tenantId, @Param("name") String name);

  /**
   * Sets {@code is_active = false} on all currently-active rows for the given tenant + name. Called
   * by {@link SchemaService#publishNewVersion} before inserting the new version.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AppointmentSchema s SET s.active = false "
          + "WHERE s.tenantId = :tenantId AND s.name = :name AND s.active = true")
  void deactivateAllByTenantIdAndName(@Param("tenantId") UUID tenantId, @Param("name") String name);

  /**
   * Finds a specific version of a schema by tenant, name, and version number. Used to validate
   * custom fields on historical jobs against the schema version they were created with (PRD §8.2).
   */
  Optional<AppointmentSchema> findByTenantIdAndNameAndVersion(
      UUID tenantId, String name, int version);
}
