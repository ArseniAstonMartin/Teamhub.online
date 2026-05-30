package com.teamhub.security.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A role groups a set of {@link Permission}s (PRD §5.4).
 *
 * <p>Built-in roles (ADMIN, DISPATCHER, TECHNICIAN) are global reference data with {@code tenantId}
 * NULL. Future tenant-custom roles (§5.4) would carry a tenant id. The {@code code} matches the
 * {@code role} claim carried in the access token.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private String code;

  /** {@code null} for built-in (global) roles; set for future tenant-custom roles (§5.4). */
  @Column(name = "tenant_id")
  private UUID tenantId;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();
}
