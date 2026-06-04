package com.teamhub.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted Expo push token bound to an authenticated user/tenant pair (PRD §15.1).
 *
 * <p>The {@code expo_token} column has a UNIQUE constraint so a single physical device is
 * represented by exactly one row, regardless of how many times it registers. On re-registration the
 * service updates {@code last_seen_at} and clears {@code stale} instead of inserting a new row. The
 * {@code stale} flag is set when Expo returns a {@code DeviceNotRegistered} receipt — stale tokens
 * are excluded from push fan-out.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "expo_token", nullable = false, unique = true)
  private String expoToken;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false, columnDefinition = "device_platform")
  private DevicePlatform platform;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt = Instant.now();

  @Column(name = "stale", nullable = false)
  private boolean stale = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public DeviceToken(UUID userId, UUID tenantId, String expoToken, DevicePlatform platform) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.expoToken = expoToken;
    this.platform = platform;
  }
}
