package com.teamhub.eventing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit tests for eventing domain objects (TASK-029).
 *
 * <p>These tests run without Spring context and exercise entity getters, builder validation guards,
 * {@link ProcessedEventId} equality/hash, and the {@link DelayedJobStatus} enum to ensure new-code
 * coverage exceeds the 80% Sonar gate.
 */
class EventingEntityTest {

  // =========================================================================
  // OutboxEvent builder and getters
  // =========================================================================

  @Test
  void outboxEvent_builder_allFieldsAccessibleViaGetters() {
    UUID tenantId = UUID.randomUUID();
    UUID aggregateId = UUID.randomUUID();
    Instant now = Instant.now();

    OutboxEvent event =
        OutboxEvent.builder()
            .tenantId(tenantId)
            .eventType("JobSubmitted")
            .payload("{\"job_id\":\"test\"}")
            .aggregateType("Job")
            .aggregateId(aggregateId)
            .createdAt(now)
            .build();

    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getEventType()).isEqualTo("JobSubmitted");
    assertThat(event.getPayload()).isEqualTo("{\"job_id\":\"test\"}");
    assertThat(event.getAggregateType()).isEqualTo("Job");
    assertThat(event.getAggregateId()).isEqualTo(aggregateId);
    assertThat(event.getCreatedAt()).isEqualTo(now);
    assertThat(event.getProcessedAt()).isNull();
    assertThat(event.getAttemptCount()).isZero();
  }

  @Test
  void outboxEvent_builder_defaultsCreatedAtToNow_whenNotSupplied() {
    Instant before = Instant.now();

    OutboxEvent event =
        OutboxEvent.builder()
            .tenantId(UUID.randomUUID())
            .eventType("JobSubmitted")
            .payload("{}")
            .aggregateType("Job")
            .aggregateId(UUID.randomUUID())
            .build();

    assertThat(event.getCreatedAt()).isAfterOrEqualTo(before);
  }

  @Test
  void outboxEvent_builder_missingEventType_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                OutboxEvent.builder()
                    .tenantId(UUID.randomUUID())
                    .payload("{}")
                    .aggregateType("Job")
                    .aggregateId(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("eventType");
  }

  @Test
  void outboxEvent_builder_missingPayload_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                OutboxEvent.builder()
                    .tenantId(UUID.randomUUID())
                    .eventType("JobSubmitted")
                    .aggregateType("Job")
                    .aggregateId(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("payload");
  }

  @Test
  void outboxEvent_builder_missingAggregateType_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                OutboxEvent.builder()
                    .tenantId(UUID.randomUUID())
                    .eventType("JobSubmitted")
                    .payload("{}")
                    .aggregateId(UUID.randomUUID())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("aggregateType");
  }

  @Test
  void outboxEvent_builder_missingAggregateId_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                OutboxEvent.builder()
                    .tenantId(UUID.randomUUID())
                    .eventType("JobSubmitted")
                    .payload("{}")
                    .aggregateType("Job")
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("aggregateId");
  }

  // =========================================================================
  // ProcessedEvent and ProcessedEventId
  // =========================================================================

  @Test
  void processedEvent_getters_returnConstructorValues() {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Instant now = Instant.now();

    ProcessedEvent pe = new ProcessedEvent(eventId, "audit-logger", now, tenantId);

    assertThat(pe.getEventId()).isEqualTo(eventId);
    assertThat(pe.getConsumerGroup()).isEqualTo("audit-logger");
    assertThat(pe.getProcessedAt()).isEqualTo(now);
    assertThat(pe.getTenantId()).isEqualTo(tenantId);
    assertThat(pe.getId()).isNotNull();
  }

  @Test
  void processedEventId_equalityAndHashCode_sameValues_areEqual() {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.now();

    ProcessedEventId idA = new ProcessedEventId(eventId, "audit-logger", now);
    ProcessedEventId idB = new ProcessedEventId(eventId, "audit-logger", now);

    assertThat(idA).isEqualTo(idB);
    assertThat(idA.hashCode()).isEqualTo(idB.hashCode());
  }

  @Test
  void processedEventId_equality_sameInstance_isEqual() {
    UUID eventId = UUID.randomUUID();
    ProcessedEventId id = new ProcessedEventId(eventId, "group", Instant.now());

    assertThat(id).isEqualTo(id);
  }

  @Test
  void processedEventId_equality_differentType_isNotEqual() {
    ProcessedEventId id = new ProcessedEventId(UUID.randomUUID(), "group", Instant.now());

    assertThat(id).isNotEqualTo("not-a-ProcessedEventId");
  }

  @Test
  void processedEventId_equality_differentValues_areNotEqual() {
    Instant now = Instant.now();
    ProcessedEventId idA = new ProcessedEventId(UUID.randomUUID(), "group-a", now);
    ProcessedEventId idB = new ProcessedEventId(UUID.randomUUID(), "group-b", now);

    assertThat(idA).isNotEqualTo(idB);
  }

  @Test
  void processedEventId_getters_returnConstructorValues() {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.now();

    ProcessedEventId id = new ProcessedEventId(eventId, "push-fan-out", now);

    assertThat(id.getEventId()).isEqualTo(eventId);
    assertThat(id.getConsumerGroup()).isEqualTo("push-fan-out");
    assertThat(id.getProcessedAt()).isEqualTo(now);
  }

  // =========================================================================
  // DelayedJob builder and getters
  // =========================================================================

  @Test
  void delayedJob_builder_allFieldsAccessibleViaGetters() {
    UUID tenantId = UUID.randomUUID();
    Instant runAt = Instant.now().plusSeconds(300);

    DelayedJob job =
        DelayedJob.builder()
            .tenantId(tenantId)
            .kind("escalate_unclaimed_job")
            .payload("{\"job_id\":\"abc\"}")
            .runAt(runAt)
            .build();

    assertThat(job.getTenantId()).isEqualTo(tenantId);
    assertThat(job.getKind()).isEqualTo("escalate_unclaimed_job");
    assertThat(job.getPayload()).isEqualTo("{\"job_id\":\"abc\"}");
    assertThat(job.getRunAt()).isEqualTo(runAt);
    assertThat(job.getStatus()).isEqualTo(DelayedJobStatus.PENDING);
    assertThat(job.getAttemptCount()).isZero();
  }

  @Test
  void delayedJob_builder_missingKind_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                DelayedJob.builder()
                    .tenantId(UUID.randomUUID())
                    .payload("{}")
                    .runAt(Instant.now())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("kind");
  }

  @Test
  void delayedJob_builder_missingPayload_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                DelayedJob.builder()
                    .tenantId(UUID.randomUUID())
                    .kind("escalate")
                    .runAt(Instant.now())
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("payload");
  }

  @Test
  void delayedJob_builder_missingRunAt_throwsIllegalState() {
    assertThatThrownBy(
            () ->
                DelayedJob.builder()
                    .tenantId(UUID.randomUUID())
                    .kind("escalate")
                    .payload("{}")
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("runAt");
  }

  @Test
  void delayedJobStatus_allValuesPresent() {
    DelayedJobStatus[] values = DelayedJobStatus.values();
    assertThat(values)
        .containsExactlyInAnyOrder(
            DelayedJobStatus.PENDING,
            DelayedJobStatus.RUNNING,
            DelayedJobStatus.DONE,
            DelayedJobStatus.FAILED,
            DelayedJobStatus.CANCELLED);
  }
}
