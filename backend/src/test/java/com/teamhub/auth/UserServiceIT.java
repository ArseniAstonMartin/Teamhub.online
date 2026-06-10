package com.teamhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@IntegrationTest
@Transactional
class UserServiceIT {

  @Autowired private UserService userService;

  @Autowired private UserRepository userRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(new Tenant("Test Org"));
  }

  @Test
  void createUser_passwordHashStartsWithBcrypt12Prefix() {
    User user =
        userService.createUser(
            tenant.getId(), "alice@example.com", "S3cr3t!", UserRole.ADMIN, "Alice");

    assertThat(user.getPasswordHash()).startsWith("$2a$12$");
  }

  @Test
  void createUser_rawPasswordNotStoredInDatabase() {
    String rawPassword = "P@ssw0rd#";
    User user =
        userService.createUser(
            tenant.getId(), "bob@example.com", rawPassword, UserRole.DISPATCHER, "Bob");

    assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
    assertThat(user.getPasswordHash()).doesNotContain(rawPassword);
  }

  @Test
  void createUser_duplicateEmailThrowsUserAlreadyExistsException() {
    userService.createUser(
        tenant.getId(), "carol@example.com", "pass1", UserRole.TECHNICIAN, "Carol");

    assertThatThrownBy(
            () ->
                userService.createUser(
                    tenant.getId(), "carol@example.com", "pass2", UserRole.ADMIN, "Carol2"))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessageContaining("carol@example.com");
  }

  @Test
  void createUser_bcryptStrengthIsExactly12() {
    String rawPassword = "strength-test";
    User user =
        userService.createUser(
            tenant.getId(), "dave@example.com", rawPassword, UserRole.ADMIN, "Dave");

    String hash = user.getPasswordHash();

    assertThat(hash).startsWith("$2a$12$");
    assertThat(BCrypt.checkpw(rawPassword, hash)).isTrue();

    assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    BCryptPasswordEncoder bcryptEncoder = (BCryptPasswordEncoder) passwordEncoder;
    String testHash = bcryptEncoder.encode("probe");
    assertThat(testHash).startsWith("$2a$12$");
  }
}
