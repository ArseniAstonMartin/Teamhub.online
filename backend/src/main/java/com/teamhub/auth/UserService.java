package com.teamhub.auth;

import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing user accounts within a tenant. */
@Service
public class UserService {

  private final UserRepository userRepository;

  private final TenantRepository tenantRepository;

  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Creates a new user with a BCrypt-hashed password. The raw password is never stored or logged.
   *
   * <p>// TO-DO: Per PRD §6.5, email uniqueness should be enforced per-tenant once multi-tenancy
   * email isolation is fully implemented. For now a globally unique constraint is used.
   *
   * @param tenantId the owning tenant
   * @param email the user's email address (case-insensitive)
   * @param rawPassword plaintext password — hashed before persistence, never logged
   * @param role the user's role within the tenant
   * @param name the user's display name
   * @return the persisted {@link User}
   * @throws UserAlreadyExistsException if the email is already registered
   * @throws IllegalArgumentException if the tenant does not exist
   */
  @Transactional
  public User createUser(
      UUID tenantId, String email, String rawPassword, UserRole role, String name) {
    if (userRepository.existsByEmail(email)) {
      throw new UserAlreadyExistsException(email);
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    String passwordHash = passwordEncoder.encode(rawPassword);

    User user = new User(tenant, email, role, name);
    user.setPasswordHash(passwordHash);

    return userRepository.save(user);
  }

  /**
   * Creates a new OAuth2 user without a password. Used when an invite is accepted via Google OAuth2
   * (PRD §6.1, TASK-021). The {@code passwordHash} is left {@code null} — such users authenticate
   * exclusively through Google and cannot use the password-login endpoint.
   *
   * @param tenantId the owning tenant
   * @param email the user's email address (case-insensitive)
   * @param role the user's role within the tenant
   * @param name the user's display name
   * @return the persisted {@link User}
   * @throws UserAlreadyExistsException if the email is already registered
   * @throws IllegalArgumentException if the tenant does not exist
   */
  @Transactional
  public User createOAuth2User(UUID tenantId, String email, UserRole role, String name) {
    if (userRepository.existsByEmail(email)) {
      throw new UserAlreadyExistsException(email);
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    User user = new User(tenant, email, role, name);
    // passwordHash intentionally null: OAuth2 users authenticate via Google, not password.
    return userRepository.save(user);
  }
}
