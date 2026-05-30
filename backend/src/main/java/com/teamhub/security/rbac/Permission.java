package com.teamhub.security.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single, atomic capability that can be granted to a {@link Role} (PRD §5.4).
 *
 * <p>The {@code code} (for example {@code job.delete.hard}) is the stable identifier referenced by
 * method-level {@code @PreAuthorize} checks; it is mapped to a Spring Security authority of the
 * form {@code PERM_<code>} at authentication time.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  public Permission(String code) {
    this.code = code;
  }
}
