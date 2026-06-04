package com.teamhub.push;

import com.teamhub.push.dto.DeviceTokenResponse;
import com.teamhub.push.dto.RegisterDeviceRequest;
import com.teamhub.push.dto.UnregisterDeviceRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device-token registration endpoints for Expo push notifications (PRD §15.1).
 *
 * <p>Both endpoints require a valid JWT (any role). The user ID is extracted from the {@code sub}
 * claim and the tenant ID from {@code tenant_id}.
 */
@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "devices")
public class DeviceController {

  private final DeviceTokenService deviceTokenService;

  public DeviceController(DeviceTokenService deviceTokenService) {
    this.deviceTokenService = deviceTokenService;
  }

  /**
   * Registers (or refreshes) an Expo push token for the authenticated user.
   *
   * <p>Idempotent: re-registering an existing token updates {@code last_seen_at} only.
   *
   * @return 200 with the upserted token details
   */
  @PostMapping("/register")
  public ResponseEntity<DeviceTokenResponse> register(
      @Valid @RequestBody RegisterDeviceRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
    DeviceTokenResponse response =
        deviceTokenService.register(userId, tenantId, request.expoToken(), request.platform());
    return ResponseEntity.ok(response);
  }

  /**
   * Removes an Expo push token for the authenticated user (called on logout).
   *
   * <p>Idempotent: no-op when the token does not exist.
   *
   * @return 204 No Content
   */
  @PostMapping("/unregister")
  public ResponseEntity<Void> unregister(
      @Valid @RequestBody UnregisterDeviceRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    deviceTokenService.unregister(userId, request.expoToken());
    return ResponseEntity.noContent().build();
  }
}
