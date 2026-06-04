package com.teamhub.push.dto;

import com.teamhub.push.DevicePlatform;
import com.teamhub.push.DeviceToken;
import java.time.Instant;
import java.util.UUID;

/** Response payload for device-token operations (PRD §15.1). */
public record DeviceTokenResponse(
    UUID id, String expoToken, DevicePlatform platform, Instant lastSeenAt) {

  public static DeviceTokenResponse from(DeviceToken token) {
    return new DeviceTokenResponse(
        token.getId(), token.getExpoToken(), token.getPlatform(), token.getLastSeenAt());
  }
}
