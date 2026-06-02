package com.teamhub.auth;

import com.teamhub.auth.dto.UserResponse;
import com.teamhub.exception.DomainException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-facing user management operations: list, deactivate, reactivate, change role (PRD §5.2,
 * TASK-055).
 *
 * <p>All mutations are tenant-scoped: every lookup uses {@code tenantId} to prevent cross-tenant
 * data access. Deactivation atomically revokes all outstanding refresh tokens so the user is locked
 * out within seconds — without waiting for the access-token TTL.
 */
@Service
public class UserManagementService {

  private final UserRepository userRepository;

  private final RefreshTokenRepository refreshTokenRepository;

  public UserManagementService(
      UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  /**
   * Returns a paginated list of all users in the given tenant.
   *
   * @param tenantId the calling Admin's tenant
   * @param pageable pagination/sort parameters
   * @return page of {@link UserResponse} projections
   */
  @Transactional(readOnly = true)
  public Page<UserResponse> listUsers(UUID tenantId, Pageable pageable) {
    return userRepository.findAllByTenantId(tenantId, pageable).map(UserResponse::from);
  }

  /**
   * Deactivates a user within the tenant.
   *
   * <ul>
   *   <li>Verifies at least one other active Admin remains after deactivation; throws {@link
   *       LastAdminProtectedException} if not.
   *   <li>Sets {@code deactivated_at = now()} and {@code is_active = false}.
   *   <li>Revokes all outstanding refresh tokens immediately.
   * </ul>
   *
   * @throws UserNotFoundException if the user does not exist in the tenant
   * @throws LastAdminProtectedException if deactivating would leave the tenant without an Admin
   */
  @Transactional
  public void deactivate(UUID tenantId, UUID targetUserId) {
    User user = requireUser(tenantId, targetUserId);

    if (user.getDeactivatedAt() != null) {
      return; // already deactivated — idempotent
    }

    if (user.getRole() == UserRole.ADMIN) {
      long activeAdminCount =
          userRepository.countByTenantIdAndRoleAndDeactivatedAtIsNull(tenantId, UserRole.ADMIN);
      if (activeAdminCount <= 1) {
        throw new LastAdminProtectedException();
      }
    }

    Instant now = Instant.now();
    user.setDeactivatedAt(now);
    user.setActive(false);
    refreshTokenRepository.revokeAllByUserId(targetUserId, now);
  }

  /**
   * Reactivates a previously deactivated user within the tenant. Clears {@code deactivated_at} and
   * restores {@code is_active = true}.
   *
   * @throws UserNotFoundException if the user does not exist in the tenant
   */
  @Transactional
  public void reactivate(UUID tenantId, UUID targetUserId) {
    User user = requireUser(tenantId, targetUserId);
    user.setDeactivatedAt(null);
    user.setActive(true);
  }

  /**
   * Changes the role of a user within the tenant.
   *
   * @param newRole one of {@code ADMIN}, {@code DISPATCHER}, {@code TECHNICIAN} (case-insensitive)
   * @throws UserNotFoundException if the user does not exist in the tenant
   * @throws InvalidRoleException if {@code newRole} is not a valid {@link UserRole}
   */
  @Transactional
  public void changeRole(UUID tenantId, UUID targetUserId, String newRole) {
    UserRole role = parseRole(newRole);
    User user = requireUser(tenantId, targetUserId);
    user.setRole(role);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private User requireUser(UUID tenantId, UUID userId) {
    return userRepository
        .findByIdAndTenantId(userId, tenantId)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  private static UserRole parseRole(String role) {
    try {
      return UserRole.valueOf(role.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new InvalidRoleException(role);
    }
  }

  /** Thrown when a {@code role} value is not one of the accepted {@link UserRole} constants. */
  public static class InvalidRoleException extends DomainException {

    public static final String CODE = "INVALID_ROLE";

    public InvalidRoleException(String role) {
      super(
          CODE,
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Invalid role '" + role + "'. Accepted values: " + Arrays.toString(UserRole.values()));
    }
  }
}
