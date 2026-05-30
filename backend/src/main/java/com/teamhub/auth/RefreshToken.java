package com.teamhub.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Server-side record of an issued refresh token (PRD §6.2).
 *
 * <p>Only the SHA-256 {@code hash} of the opaque token is persisted — never the raw value. A token
 * is valid while {@code revokedAt} is null and {@code expiresAt} is in the future. Rotation revokes
 * the presented token and inserts a new row; logout revokes without re-issuing.
 *
 * <p>{@code userId} / {@code tenantId} are stored as plain UUID columns rather than associations so
 * that lookup-by-hash during refresh/logout works without an established tenant context.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private String hash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public RefreshToken(UUID userId, UUID tenantId, String hash, Instant expiresAt) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.hash = hash;
    this.expiresAt = expiresAt;
  }

  /** True if the token has not been revoked and has not expired at {@code now}. */
  public boolean isActiveAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }
}
