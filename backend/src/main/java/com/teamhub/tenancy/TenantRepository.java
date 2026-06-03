package com.teamhub.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data repository for {@link Tenant} entities. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  /** Looks up a tenant by Stripe customer ID for webhook event routing (TASK-063, PRD §17.3). */
  Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);

  /**
   * Returns all tenants that have an active Stripe subscription (TASK-066, PRD §28).
   *
   * <p>Used by the daily reconciliation job to check for plan/period drift against the Stripe API.
   */
  @Query("SELECT t FROM Tenant t WHERE t.stripeSubscriptionId IS NOT NULL")
  List<Tenant> findAllWithActiveSubscription();
}
