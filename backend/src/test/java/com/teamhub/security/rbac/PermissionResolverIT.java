package com.teamhub.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.TestcontainersConfiguration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the seeded role -> permission model (TASK-023 acceptance criteria 1, 2, 4 and test step
 * 1): ADMIN holds every seeded permission, the hierarchy is strictly additive, and the
 * admin-only/dispatcher-only sensitivities from PRD §5.3 hold.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PermissionResolverIT {

  @Autowired private PermissionResolver permissionResolver;

  @Autowired private PermissionRepository permissionRepository;

  @Test
  void admin_hasEverySeededPermission() {
    Set<String> allPermissions =
        permissionRepository.findAll().stream()
            .map(Permission::getCode)
            .collect(Collectors.toUnmodifiableSet());

    assertThat(permissionResolver.resolvePermissionCodes("ADMIN"))
        .containsExactlyInAnyOrderElementsOf(allPermissions);
  }

  @Test
  void dispatcher_lacksHardDelete_butKeepsTenantReads() {
    Set<String> dispatcher = permissionResolver.resolvePermissionCodes("DISPATCHER");

    assertThat(dispatcher).doesNotContain("job.delete.hard");
    assertThat(dispatcher).contains("job.read.tenant", "job.create", "job.archive");
  }

  @Test
  void technician_lacksSchemaEdit_butKeepsOwnJobReads() {
    Set<String> technician = permissionResolver.resolvePermissionCodes("TECHNICIAN");

    assertThat(technician).doesNotContain("schema.edit", "job.delete.hard", "job.read.tenant");
    assertThat(technician).contains("job.read.own", "job.claim");
  }

  @Test
  void hierarchyIsStrictlyAdditive_adminSupersetOfDispatcherSupersetOfTechnician() {
    Set<String> admin = permissionResolver.resolvePermissionCodes("ADMIN");
    Set<String> dispatcher = permissionResolver.resolvePermissionCodes("DISPATCHER");
    Set<String> technician = permissionResolver.resolvePermissionCodes("TECHNICIAN");

    assertThat(admin).containsAll(dispatcher);
    assertThat(dispatcher).containsAll(technician);
    assertThat(dispatcher).isNotEqualTo(technician);
    assertThat(admin).isNotEqualTo(dispatcher);
  }

  @Test
  void unknownRole_resolvesToEmpty() {
    assertThat(permissionResolver.resolvePermissionCodes("NOPE")).isEmpty();
    assertThat(permissionResolver.resolvePermissionCodes(null)).isEmpty();
  }
}
