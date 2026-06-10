package com.teamhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link AuditLog} entity and its {@link AuditLog.Builder}.
 *
 * <p>No Spring context or database required — all assertions are on in-memory objects.
 */
@Tag("unit")
class AuditLogTest {

  private static final UUID TENANT = UUID.randomUUID();

  // -------------------------------------------------------------------------
  // Builder: optional field setters (actorUserId, beforeState, afterState)
  // -------------------------------------------------------------------------

  @Test
  void builder_withAllOptionalFields_setsGettersCorrectly() {
    UUID actor = UUID.randomUUID();
    UUID resource = UUID.randomUUID();
    Instant now = Instant.now();
    Object before = "{}";
    Object after = "{\"x\":1}";

    AuditLog log =
        AuditLog.builder()
            .tenantId(TENANT)
            .actorUserId(actor)
            .action("job.create")
            .resourceType("job")
            .resourceId(resource)
            .beforeState(before)
            .afterState(after)
            .ipAddress("10.0.0.1")
            .userAgent("TestAgent/1.0")
            .occurredAt(now)
            .build();

    assertThat(log.getTenantId()).isEqualTo(TENANT);
    assertThat(log.getActorUserId()).isEqualTo(actor);
    assertThat(log.getAction()).isEqualTo("job.create");
    assertThat(log.getResourceType()).isEqualTo("job");
    assertThat(log.getResourceId()).isEqualTo(resource);
    assertThat(log.getBeforeState()).isEqualTo(before);
    assertThat(log.getAfterState()).isEqualTo(after);
    assertThat(log.getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(log.getUserAgent()).isEqualTo("TestAgent/1.0");
    assertThat(log.getOccurredAt()).isEqualTo(now);
    // getId() returns null for a non-persisted entity (no JPA-generated UUID yet).
    assertThat(log.getId()).isNull();
  }

  @Test
  void builder_withoutOccurredAt_defaultsToNow() {
    Instant before = Instant.now();
    AuditLog log =
        AuditLog.builder().tenantId(TENANT).action("job.create").resourceType("job").build();
    Instant after = Instant.now();

    assertThat(log.getOccurredAt()).isNotNull().isBetween(before, after);
  }

  // -------------------------------------------------------------------------
  // Builder: validation branches in build()
  // -------------------------------------------------------------------------

  @Test
  void builder_withNullTenantId_throwsIllegalStateException() {
    assertThatThrownBy(() -> AuditLog.builder().action("job.create").resourceType("job").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void builder_withNullAction_throwsIllegalStateException() {
    assertThatThrownBy(() -> AuditLog.builder().tenantId(TENANT).resourceType("job").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("action");
  }

  @Test
  void builder_withBlankAction_throwsIllegalStateException() {
    assertThatThrownBy(
            () -> AuditLog.builder().tenantId(TENANT).action("  ").resourceType("job").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("action");
  }

  @Test
  void builder_withNullResourceType_throwsIllegalStateException() {
    assertThatThrownBy(() -> AuditLog.builder().tenantId(TENANT).action("job.create").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resourceType");
  }

  @Test
  void builder_withBlankResourceType_throwsIllegalStateException() {
    assertThatThrownBy(
            () ->
                AuditLog.builder()
                    .tenantId(TENANT)
                    .action("job.create")
                    .resourceType("   ")
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resourceType");
  }
}
