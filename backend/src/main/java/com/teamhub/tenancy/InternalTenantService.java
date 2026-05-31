package com.teamhub.tenancy;

import com.teamhub.auth.User;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.schemas.SchemaPreset;
import com.teamhub.schemas.SchemaPresetRepository;
import com.teamhub.schemas.SchemaService;
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

  private final SchemaPresetRepository schemaPresetRepository;

  private final SchemaService schemaService;

  public InternalTenantService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      SchemaPresetRepository schemaPresetRepository,
      SchemaService schemaService) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.schemaPresetRepository = schemaPresetRepository;
    this.schemaService = schemaService;
  }

  /**
   * Creates a new tenant with plan FREE and a single Admin user for the given owner email. No
   * appointment schema is created.
   *
   * @param name the display name of the tenant organisation
   * @param ownerEmail the email address for the first admin user
   * @return the persisted {@link Tenant}
   * @throws TenantProvisionException if the email address is already registered
   */
  @Transactional
  public Tenant provision(String name, String ownerEmail) {
    return provision(name, ownerEmail, null);
  }

  /**
   * Creates a new tenant with plan FREE, a single Admin user, and optionally instantiates the first
   * appointment-schema row from a predefined preset (PRD §8.4).
   *
   * <p>When {@code presetKey} is non-null:
   *
   * <ol>
   *   <li>The matching {@link SchemaPreset} row is loaded from {@code schema_presets}.
   *   <li>{@link SchemaService#publishNewVersion} is called to create version 1 of the preset
   *       schema for the new tenant.
   * </ol>
   *
   * <p>When {@code presetKey} is {@code null}, no appointment schema is created (backward
   * compatible with the no-arg overload).
   *
   * @param name the display name of the tenant organisation
   * @param ownerEmail the email address for the first admin user
   * @param presetKey the key of the predefined schema preset to apply (e.g. {@code "plumber"}), or
   *     {@code null} for no preset
   * @return the persisted {@link Tenant}
   * @throws TenantProvisionException if the email address is already registered, or if the given
   *     presetKey does not match any row in {@code schema_presets}
   */
  @Transactional
  public Tenant provision(String name, String ownerEmail, String presetKey) {
    if (userRepository.existsByEmail(ownerEmail)) {
      throw new TenantProvisionException("Email already registered: " + ownerEmail);
    }

    Tenant tenant = tenantRepository.save(new Tenant(name));

    User admin = new User(tenant, ownerEmail, UserRole.ADMIN, name);
    userRepository.save(admin);

    if (presetKey != null) {
      // TO-DO (PRD §26.1): confirm final top-5 makes list for the locksmith preset before release.
      SchemaPreset preset =
          schemaPresetRepository
              .findById(presetKey)
              .orElseThrow(
                  () -> new TenantProvisionException("Unknown preset key: '" + presetKey + "'"));
      schemaService.publishNewVersion(
          tenant.getId(), preset.getSchemaName(), preset.getFieldDefinitions());
    }

    return tenant;
  }
}
