package com.teamhub.tenancy;

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

/** Represents an organisation (tenant) in the system. */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Plan plan = Plan.FREE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "payment_failed_at")
  private Instant paymentFailedAt;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "close_jobs_enabled", nullable = false)
  private boolean closeJobsEnabled = false;

  public Tenant(String name) {
    this.name = name;
  }
}
