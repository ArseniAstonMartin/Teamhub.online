package com.teamhub.security.rbac;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a role code (carried in the access-token {@code role} claim) into its set of permission
 * codes using the data-driven model seeded in {@code V4__roles_permissions.sql} (PRD §5.4).
 *
 * <p>This is the single place the authentication layer consults to translate a role into granted
 * authorities, so adding a new role (e.g. {@code MANAGER} in v2.0) requires only seed data — no
 * code or controller changes.
 *
 * <p>Resolution runs during JWT authentication, before any tenant context/GUC is established, so it
 * reads the global (tenant_id NULL) built-in roles as the RLS-exempt login user.
 *
 * <p>No caching is applied: a role lookup is a single small indexed join and changes to seed data
 * (including a newly inserted role) take effect on the next request. See the progress-log "Notes
 * for next iteration" for a future caching/eviction strategy.
 */
@Service
public class PermissionResolver {

  private final RoleRepository roleRepository;

  public PermissionResolver(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  /**
   * Resolves the permission codes granted to the given built-in role code.
   *
   * @param roleCode the role code from the JWT {@code role} claim (may be {@code null})
   * @return the set of permission codes; empty if the role is unknown or {@code null}
   */
  @Transactional(readOnly = true)
  public Set<String> resolvePermissionCodes(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return Set.of();
    }
    return roleRepository
        .findByCodeAndTenantIdIsNull(roleCode)
        .map(
            role ->
                role.getPermissions().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toUnmodifiableSet()))
        .orElseGet(Set::of);
  }
}
