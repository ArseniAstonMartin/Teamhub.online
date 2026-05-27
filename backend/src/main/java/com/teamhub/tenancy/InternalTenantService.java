package com.teamhub.tenancy;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal service for provisioning new tenants. Not exposed via REST; called only by
 * staff-initiated onboarding flows (PRD §6.4).
 */
@Service
public class InternalTenantService {

  private final TenantRepository tenantRepository;

  private final UserRepository userRepository;

  public InternalTenantService(TenantRepository tenantRepository, UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  /**
   * Creates a new tenant with plan FREE and a single Admin user for the given owner email.
   *
   * @param name the display name of the tenant organisation
   * @param ownerEmail the email address for the first admin user
   * @return the persisted {@link Tenant}
   * @throws TenantProvisionException if the email address is already registered
   */
  @Transactional
  public Tenant provision(String name, String ownerEmail) {
    if (userRepository.existsByEmail(ownerEmail)) {
      throw new TenantProvisionException("Email already registered: " + ownerEmail);
    }

    Tenant tenant = tenantRepository.save(new Tenant(name));

    User admin = new User(tenant, ownerEmail, UserRole.ADMIN, name);
    userRepository.save(admin);

    return tenant;
  }
}
