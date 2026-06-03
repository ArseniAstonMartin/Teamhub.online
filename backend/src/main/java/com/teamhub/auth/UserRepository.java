package com.teamhub.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link User} entities. */
public interface UserRepository extends JpaRepository<User, UUID> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  /** Returns all users belonging to a tenant, ordered by the given {@link Pageable}. */
  Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);

  /** Finds a specific user within a tenant — used to enforce tenant isolation. */
  Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Counts active (non-deactivated) Admins in the tenant. Used by the last-admin protection guard.
   */
  long countByTenantIdAndRoleAndDeactivatedAtIsNull(UUID tenantId, UserRole role);

  /**
   * Counts all active (non-deactivated) users in the tenant regardless of role (TASK-064).
   *
   * <p>Used by {@code QuotaEnforcementService} to enforce seat limits before a new invite is sent.
   */
  long countByTenantIdAndDeactivatedAtIsNull(UUID tenantId);
}
