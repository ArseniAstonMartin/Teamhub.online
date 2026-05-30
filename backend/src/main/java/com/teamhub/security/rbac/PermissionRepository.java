package com.teamhub.security.rbac;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Access to {@link Permission} reference data. */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

  /**
   * Finds a permission by its code.
   *
   * @param code the permission code (for example {@code job.read.tenant})
   * @return the permission, if present
   */
  Optional<Permission> findByCode(String code);
}
