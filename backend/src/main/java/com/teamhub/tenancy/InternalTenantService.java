package com.teamhub.tenancy;

import com.teamhub.auth.InviteRepository;
import com.teamhub.auth.User;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.UserRole;
import com.teamhub.schemas.SchemaPreset;
import com.teamhub.schemas.SchemaPresetRepository;
import com.teamhub.schemas.SchemaService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
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

  private final InviteRepository inviteRepository;

  private final SchemaPresetRepository schemaPresetRepository;

  private final SchemaService schemaService;

  private final PasswordEncoder passwordEncoder;

  public InternalTenantService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      InviteRepository inviteRepository,
      SchemaPresetRepository schemaPresetRepository,
      SchemaService schemaService,
      PasswordEncoder passwordEncoder) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.inviteRepository = inviteRepository;
    this.schemaPresetRepository = schemaPresetRepository;
    this.schemaService = schemaService;
    this.passwordEncoder = passwordEncoder;
  }

  /** Returns whether a tenant with the given id exists. */
  @Transactional(readOnly = true)
  public boolean tenantExists(UUID tenantId) {
    return tenantRepository.existsById(tenantId);
  }

  /**
   * Creates a tenant plus an Admin user with a password — integration-test fixture helper only.
   *
   * <p>Production onboarding uses invite-only {@link #provision(String, String, String)} via {@link
   * com.teamhub.internal.InternalProvisioningService}.
   */
  @Transactional
  public Tenant provisionWithAdminUser(String name, String ownerEmail) {
    return provisionWithAdminUser(name, ownerEmail, null, "TestPassword1!");
  }

  /**
   * Creates a tenant, optional preset schema, and an Admin user — integration-test fixture helper.
   */
  @Transactional
  public Tenant provisionWithAdminUser(
      String name, String ownerEmail, String presetKey, String rawPassword) {
    Tenant tenant = provision(name, ownerEmail, presetKey);
    User admin = new User(tenant, ownerEmail, UserRole.ADMIN, name);
    admin.setPasswordHash(passwordEncoder.encode(rawPassword));
    userRepository.save(admin);
    return tenant;
  }

  /** Three-arg overload for tests that apply a schema preset. */
  @Transactional
  public Tenant provisionWithAdminUser(String name, String ownerEmail, String presetKey) {
    return provisionWithAdminUser(name, ownerEmail, presetKey, "TestPassword1!");
  }

  /**
   * Creates a new tenant with plan FREE. No user is created — the owner is onboarded via invite
   * (PRD §6.4, TASK-131).
   *
   * @param name the display name of the tenant organisation
   * @param ownerEmail reserved owner email (must not already be registered)
   * @return the persisted {@link Tenant}
   * @throws TenantProvisionException if the email address is already registered
   */
  @Transactional
  public Tenant provision(String name, String ownerEmail) {
    return provision(name, ownerEmail, null);
  }

  /**
   * Creates a new tenant with plan FREE and optionally instantiates the first appointment-schema
   * row from a predefined preset (PRD §8.4). No user is created — invite-only onboarding.
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
    if (userRepository.existsByEmail(ownerEmail)
        || !inviteRepository.findByEmailAndAcceptedAtIsNull(ownerEmail).isEmpty()) {
      throw new TenantProvisionException("Email already registered: " + ownerEmail);
    }

    Tenant tenant = tenantRepository.save(new Tenant(name));

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
