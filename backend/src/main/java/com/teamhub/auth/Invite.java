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
 * A one-time-use invite token that allows an email recipient to join a tenant with a given role
 * (PRD §6.5, TASK-054).
 *
 * <p>Only the SHA-256 {@code tokenHash} of the raw opaque token is persisted. An invite is consumed
 * by setting {@code acceptedAt}; expired tokens ({@code expiresAt} in the past) are also rejected.
 */
@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
public class Invite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private String email;

  @Column(nullable = false)
  private String role;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "resent_at")
  private Instant resentAt;

  public Invite(UUID tenantId, String email, String role, String tokenHash, Instant expiresAt) {
    this.tenantId = tenantId;
    this.email = email;
    this.role = role;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  /** True only when the invite has not been accepted and has not expired at {@code now}. */
  public boolean isActiveAt(Instant now) {
    return acceptedAt == null && expiresAt.isAfter(now);
  }
}
