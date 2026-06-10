package com.teamhub.auth;

import com.teamhub.tenancy.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An authenticated user belonging to a tenant. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
  private Tenant tenant;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String email;

  /** Hashed password — populated by the auth service (TASK-017). */
  @Column(name = "password_hash")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  private String name;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  public User(Tenant tenant, String email, UserRole role, String name) {
    this.tenant = tenant;
    this.email = email;
    this.role = role;
    this.name = name;
  }
}
