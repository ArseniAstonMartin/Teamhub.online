package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.auth.UserRepository;
import com.teamhub.config.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@IntegrationTest
@Transactional
class InternalTenantServiceIT {

  @Autowired private InternalTenantService service;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private UserRepository userRepository;

  @Test
  void provision_createsTenantWithoutUser() {
    long usersBefore = userRepository.count();

    Tenant tenant = service.provision("Acme Plumbing", "owner@acme.com");

    assertThat(tenant.getId()).isNotNull();
    assertThat(tenant.getName()).isEqualTo("Acme Plumbing");
    assertThat(userRepository.count()).isEqualTo(usersBefore);
  }

  @Test
  void provision_planDefaultsToFree() {
    Tenant tenant = service.provision("Free Corp", "free@example.com");

    Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
    assertThat(persisted.getPlan()).isEqualTo(Plan.FREE);
  }

  @Test
  void provision_duplicateEmailThrowsDomainException() {
    service.provisionWithAdminUser("First Org", "shared@example.com");

    assertThatThrownBy(() -> service.provision("Second Org", "shared@example.com"))
        .isInstanceOf(TenantProvisionException.class)
        .hasMessageContaining("shared@example.com");
  }

  @Test
  void provision_twoCallsWithDifferentEmailsSucceed() {
    // Capture the pre-test count so the assertion is not brittle against sentinel/seed rows
    // inserted by Flyway migrations (e.g. V9 adds the all-zeros sentinel tenant).
    long countBefore = tenantRepository.count();

    UUID id1 = service.provision("Org One", "one@example.com").getId();
    UUID id2 = service.provision("Org Two", "two@example.com").getId();

    assertThat(id1).isNotEqualTo(id2);
    assertThat(tenantRepository.count()).isEqualTo(countBefore + 2);
  }
}
