package com.teamhub.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Invite} entities (TASK-054). */
public interface InviteRepository extends JpaRepository<Invite, UUID> {

  Optional<Invite> findByTokenHash(String tokenHash);

  List<Invite> findByTenantIdAndEmailAndAcceptedAtIsNull(UUID tenantId, String email);

  /**
   * Finds all pending (not-yet-accepted) invites for a given email across all tenants.
   *
   * <p>Used by the Google OAuth2 success handler to resolve the tenant when there is no tenant
   * context during the OAuth2 callback. If exactly one active invite is found, its tenant is used;
   * zero or multiple active invites are treated as "no invite" (TASK-021).
   */
  List<Invite> findByEmailAndAcceptedAtIsNull(String email);

  Optional<Invite> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Finds the most recent pending invite for a tenant and role (TASK-131 owner resend). */
  Optional<Invite> findFirstByTenantIdAndRoleAndAcceptedAtIsNullOrderByCreatedAtDesc(
      UUID tenantId, String role);
}
