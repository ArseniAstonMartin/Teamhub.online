package com.teamhub.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link DeviceToken} (PRD §15.1). */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

  /** Looks up a token by its Expo push token string; used for upsert on registration. */
  Optional<DeviceToken> findByExpoToken(String expoToken);

  /** Returns all non-stale tokens for a tenant; used during push fan-out. */
  List<DeviceToken> findByTenantIdAndStaleIsFalse(UUID tenantId);

  /** Looks up a token by owner and Expo token string; used for unregister. */
  Optional<DeviceToken> findByUserIdAndExpoToken(UUID userId, String expoToken);

  /**
   * Returns all non-stale tokens for a specific role within a tenant.
   *
   * <p>Used for role-targeted fan-out (e.g. tech-only notifications). The native query joins {@code
   * device_tokens} with {@code users} to filter by the user's current role.
   */
  @Query(
      value =
          """
          SELECT dt.*
          FROM device_tokens dt
          JOIN users u ON u.id = dt.user_id
          WHERE dt.tenant_id = :tenantId
            AND dt.stale = false
            AND CAST(u.role AS TEXT) = :role
          """,
      nativeQuery = true)
  List<DeviceToken> findByTenantIdAndUserRoleAndStaleIsFalse(
      @Param("tenantId") UUID tenantId, @Param("role") String role);
}
