package com.teamhub.auth;

import com.teamhub.auth.dto.CurrentUserResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the authenticated caller's profile (TASK-132, PRD §6.2). */
@Service
public class CurrentUserService {

  private final UserRepository userRepository;

  public CurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Returns the profile for the JWT subject within the caller's tenant.
   *
   * @throws UserNotFoundException if the user does not exist in the tenant
   * @throws AccountDeactivatedException if the user has been deactivated
   */
  @Transactional(readOnly = true)
  public CurrentUserResponse getCurrentUser(UUID tenantId, UUID userId) {
    User user =
        userRepository
            .findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new UserNotFoundException(userId));

    if (!user.isActive()) {
      throw new AccountDeactivatedException();
    }

    return CurrentUserResponse.from(user);
  }
}
