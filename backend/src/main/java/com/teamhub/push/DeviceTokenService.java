package com.teamhub.push;

import com.teamhub.push.dto.DeviceTokenResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages Expo device tokens for push-notification delivery (PRD §15.1).
 *
 * <p>Registration is idempotent: re-registering an existing {@code expo_token} refreshes {@code
 * last_seen_at} without inserting a duplicate row. Unregistration is also idempotent; calling it on
 * an unknown token is a no-op. Stale marking is invoked by the Expo push provider (TASK-057) when a
 * {@code DeviceNotRegistered} receipt is received.
 */
@Service
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;

  public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
    this.deviceTokenRepository = deviceTokenRepository;
  }

  /**
   * Upserts a device token for the authenticated user.
   *
   * <p>If the {@code expoToken} already exists in the table the row's {@code last_seen_at} is
   * refreshed and {@code stale} is cleared. Otherwise a new row is inserted.
   */
  @Transactional
  public DeviceTokenResponse register(
      UUID userId, UUID tenantId, String expoToken, DevicePlatform platform) {

    DeviceToken token =
        deviceTokenRepository
            .findByExpoToken(expoToken)
            .map(
                existing -> {
                  existing.setLastSeenAt(Instant.now());
                  existing.setStale(false);
                  return existing;
                })
            .orElseGet(() -> new DeviceToken(userId, tenantId, expoToken, platform));

    return DeviceTokenResponse.from(deviceTokenRepository.save(token));
  }

  /**
   * Deletes a device token owned by the given user.
   *
   * <p>No-op when the token does not exist or belongs to a different user.
   */
  @Transactional
  public void unregister(UUID userId, String expoToken) {
    deviceTokenRepository
        .findByUserIdAndExpoToken(userId, expoToken)
        .ifPresent(deviceTokenRepository::delete);
  }

  /**
   * Marks a token stale after a {@code DeviceNotRegistered} Expo receipt.
   *
   * <p>Stale tokens are excluded from future push fan-out queries. Called by the Expo push provider
   * (TASK-057); no-op when the token is not found.
   */
  @Transactional
  public void markStale(String expoToken) {
    deviceTokenRepository
        .findByExpoToken(expoToken)
        .ifPresent(
            token -> {
              token.setStale(true);
              deviceTokenRepository.save(token);
            });
  }
}
