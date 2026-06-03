package com.teamhub.tenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Tenant} entities. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  /** Looks up a tenant by Stripe customer ID for webhook event routing (TASK-063, PRD §17.3). */
  Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
}
