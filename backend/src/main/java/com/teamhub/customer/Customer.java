package com.teamhub.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

/** Customer record within a tenant's workspace (PRD §11.1). */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column(name = "company_name")
  private String companyName;

  /** Phone number in E.164 format (e.g. +15033334444). */
  @Column(nullable = false)
  private String phone;

  private String email;

  @Column(name = "address_line", nullable = false)
  private String addressLine;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false)
  private String zip;

  /**
   * Service address geocoded via Google Maps (PRD §11.3). Stored as PostGIS geography(Point,4326)
   * so that ST_DWithin and related spatial functions operate on geodesic distances (metres).
   *
   * <p>JTS coordinate order: x = longitude, y = latitude.
   */
  @Column(nullable = false, columnDefinition = "geography(Point,4326)")
  private Point location;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Soft-delete timestamp; null means the customer is active (PRD §11.3). */
  @Column(name = "archived_at")
  private Instant archivedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
