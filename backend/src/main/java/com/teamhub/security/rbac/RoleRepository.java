package com.teamhub.security.rbac;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Access to {@link Role} reference data and its associated permissions. */
public interface RoleRepository extends JpaRepository<Role, UUID> {

  /**
   * Finds a built-in (global) role by its code.
   *
   * @param code the role code (for example {@code ADMIN}), matching the JWT {@code role} claim
   * @return the global role, if present
   */
  Optional<Role> findByCodeAndTenantIdIsNull(String code);
}
