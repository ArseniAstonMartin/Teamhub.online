package com.teamhub.internal;

import com.teamhub.auth.InvalidCredentialsException;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates global staff accounts for the internal console (PRD §6.4). */
@Service
public class StaffAuthService {

  private final StaffUserRepository staffUserRepository;

  private final PasswordEncoder passwordEncoder;

  private final StaffJwtService staffJwtService;

  public StaffAuthService(
      StaffUserRepository staffUserRepository,
      PasswordEncoder passwordEncoder,
      StaffJwtService staffJwtService) {
    this.staffUserRepository = staffUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.staffJwtService = staffJwtService;
  }

  /** Issued staff session: access token only (no refresh token for internal console). */
  public record StaffSession(String accessToken, java.time.Instant expiresAt) {}

  /**
   * Authenticates staff email/password and issues a staff JWT.
   *
   * @throws InvalidCredentialsException when credentials are invalid or the account is inactive
   */
  @Transactional(readOnly = true)
  public StaffSession login(String email, String rawPassword) {
    Optional<StaffUser> maybeStaff = staffUserRepository.findByEmail(email);

    if (maybeStaff.isEmpty()) {
      throw new InvalidCredentialsException();
    }

    StaffUser staff = maybeStaff.get();

    if (!staff.isActive() || !passwordEncoder.matches(rawPassword, staff.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    StaffJwtService.StaffAccessToken token = staffJwtService.issue(staff);
    return new StaffSession(token.value(), token.expiresAt());
  }
}
