package com.teamhub.eventing.payloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit tests for event payload records (TASK-034).
 *
 * <p>Validates Jackson round-trip serialization/deserialization for every PRD §22.5.5 event type,
 * forward-compatibility (unknown fields do not throw), and append-only evolution (old payload
 * without a new field deserializes successfully with the field null/default).
 */
@Tag("unit")
class EventPayloadRecordTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    // Mirror Spring Boot's default ObjectMapper: unknown properties are ignored and dates are
    // written as ISO-8601 strings, not timestamps.
    mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  // =========================================================================
  // Step 1: Round-trip tests for all 8 event types
  // =========================================================================

  @Test
  void jobSubmitted_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Instant scheduledAt = Instant.parse("2026-06-01T10:00:00Z");

    JobSubmitted original =
        new JobSubmitted(eventId, tenantId, jobId, customerId, "PLUMBING", scheduledAt);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"tenant_id\"");
    assertThat(json).contains("\"job_id\"");
    assertThat(json).contains("\"customer_id\"");
    assertThat(json).contains("\"service_type\"");
    assertThat(json).contains("\"scheduled_at\"");

    JobSubmitted deserialized = mapper.readValue(json, JobSubmitted.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void jobClaimed_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID technicianId = UUID.randomUUID();
    Instant claimedAt = Instant.parse("2026-06-01T11:00:00Z");

    JobClaimed original = new JobClaimed(eventId, tenantId, jobId, technicianId, claimedAt);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"tenant_id\"");
    assertThat(json).contains("\"job_id\"");
    assertThat(json).contains("\"technician_id\"");
    assertThat(json).contains("\"claimed_at\"");

    JobClaimed deserialized = mapper.readValue(json, JobClaimed.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void jobStatusChanged_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    JobStatusChanged original =
        new JobStatusChanged(
            eventId, tenantId, jobId, "SUBMITTED", "ASSIGNED_TO_TECHNICIAN", actorId);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"old_status\"");
    assertThat(json).contains("\"new_status\"");
    assertThat(json).contains("\"actor_id\"");

    JobStatusChanged deserialized = mapper.readValue(json, JobStatusChanged.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void jobCompleted_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID technicianId = UUID.randomUUID();
    Instant completedAt = Instant.parse("2026-06-01T15:30:00Z");

    JobCompleted original = new JobCompleted(eventId, tenantId, jobId, technicianId, completedAt);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"job_id\"");
    assertThat(json).contains("\"technician_id\"");
    assertThat(json).contains("\"completed_at\"");

    JobCompleted deserialized = mapper.readValue(json, JobCompleted.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void quotaThresholdReached_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    QuotaThresholdReached original = new QuotaThresholdReached(eventId, tenantId, 80, 198, 250);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"threshold_pct\"");
    assertThat(json).contains("\"current_count\"");
    assertThat(json).contains("\"limit\"");

    QuotaThresholdReached deserialized = mapper.readValue(json, QuotaThresholdReached.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void subscriptionUpdated_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    Instant effectiveAt = Instant.parse("2026-06-01T00:00:00Z");

    SubscriptionUpdated original =
        new SubscriptionUpdated(
            eventId, tenantId, "sub_ABC123", "STARTER", "PROFESSIONAL", effectiveAt);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"stripe_subscription_id\"");
    assertThat(json).contains("\"old_plan\"");
    assertThat(json).contains("\"new_plan\"");
    assertThat(json).contains("\"effective_at\"");

    SubscriptionUpdated deserialized = mapper.readValue(json, SubscriptionUpdated.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void userInvited_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID invitedBy = UUID.randomUUID();
    Instant expiresAt = Instant.parse("2026-06-08T10:00:00Z");

    UserInvited original =
        new UserInvited(
            eventId,
            tenantId,
            "tech@example.com",
            invitedBy,
            "TECHNICIAN",
            "tok_signed_abc",
            expiresAt);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"invited_user_email\"");
    assertThat(json).contains("\"invited_by\"");
    assertThat(json).contains("\"role\"");
    assertThat(json).contains("\"invite_token\"");
    assertThat(json).contains("\"expires_at\"");

    UserInvited deserialized = mapper.readValue(json, UserInvited.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void photoUploaded_roundTrip() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();
    UUID uploadedBy = UUID.randomUUID();

    PhotoUploaded original =
        new PhotoUploaded(
            eventId,
            tenantId,
            jobId,
            photoId,
            "https://s3.example.com/tenant/jobs/photo.jpg",
            uploadedBy);

    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"event_id\"");
    assertThat(json).contains("\"job_id\"");
    assertThat(json).contains("\"photo_id\"");
    assertThat(json).contains("\"photo_url\"");
    assertThat(json).contains("\"uploaded_by\"");

    PhotoUploaded deserialized = mapper.readValue(json, PhotoUploaded.class);
    assertThat(deserialized).isEqualTo(original);
  }

  // =========================================================================
  // Forward-compatibility: unknown extra fields must not throw
  // =========================================================================

  @Test
  void jobSubmitted_forwardCompat_unknownExtraField_doesNotThrow() {
    String jsonWithExtra =
        "{\"event_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"tenant_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"job_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"customer_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"service_type\":\"PLUMBING\","
            + "\"scheduled_at\":\"2026-06-01T10:00:00Z\","
            + "\"unknown_future_field\":\"some_value\"}";

    assertThatCode(() -> mapper.readValue(jsonWithExtra, JobSubmitted.class))
        .doesNotThrowAnyException();
  }

  @Test
  void jobClaimed_forwardCompat_unknownExtraField_doesNotThrow() {
    String jsonWithExtra =
        "{\"event_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"tenant_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"job_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"technician_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"claimed_at\":\"2026-06-01T11:00:00Z\","
            + "\"future_field\":42}";

    assertThatCode(() -> mapper.readValue(jsonWithExtra, JobClaimed.class))
        .doesNotThrowAnyException();
  }

  @Test
  void jobStatusChanged_forwardCompat_unknownExtraField_doesNotThrow() {
    String jsonWithExtra =
        "{\"event_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"tenant_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"job_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"old_status\":\"SUBMITTED\","
            + "\"new_status\":\"ASSIGNED_TO_TECHNICIAN\","
            + "\"actor_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"extra\":true}";

    assertThatCode(() -> mapper.readValue(jsonWithExtra, JobStatusChanged.class))
        .doesNotThrowAnyException();
  }

  @Test
  void quotaThresholdReached_forwardCompat_unknownExtraField_doesNotThrow() {
    String jsonWithExtra =
        "{\"event_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"tenant_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"threshold_pct\":80,"
            + "\"current_count\":200,"
            + "\"limit\":250,"
            + "\"new_v2_field\":\"ignored\"}";

    assertThatCode(() -> mapper.readValue(jsonWithExtra, QuotaThresholdReached.class))
        .doesNotThrowAnyException();
  }

  @Test
  void userInvited_forwardCompat_unknownExtraField_doesNotThrow() {
    String jsonWithExtra =
        "{\"event_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"tenant_id\":\""
            + UUID.randomUUID()
            + "\","
            + "\"invited_user_email\":\"tech@example.com\","
            + "\"invited_by\":\""
            + UUID.randomUUID()
            + "\","
            + "\"role\":\"TECHNICIAN\","
            + "\"invite_token\":\"tok_abc\","
            + "\"expires_at\":\"2026-06-08T10:00:00Z\","
            + "\"v2_metadata\":{}}";

    assertThatCode(() -> mapper.readValue(jsonWithExtra, UserInvited.class))
        .doesNotThrowAnyException();
  }

  // =========================================================================
  // Step 2: Append-only forward-compat — old payload parses with new nullable field
  // (proves schema evolution: add a field with null default; existing JSON still works)
  // =========================================================================

  @Test
  void jobSubmitted_appendOnly_oldJsonParsesWithNewNullableField() throws Exception {
    // Simulate "old" payload without a hypothetical new field (e.g. priority)
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    String oldJson =
        "{\"event_id\":\""
            + eventId
            + "\","
            + "\"tenant_id\":\""
            + tenantId
            + "\","
            + "\"job_id\":\""
            + jobId
            + "\","
            + "\"customer_id\":\""
            + customerId
            + "\","
            + "\"service_type\":\"ELECTRICAL\"}";

    // scheduledAt field is absent — must deserialize with scheduledAt = null
    JobSubmitted deserialized = mapper.readValue(oldJson, JobSubmitted.class);

    assertThat(deserialized.eventId()).isEqualTo(eventId);
    assertThat(deserialized.tenantId()).isEqualTo(tenantId);
    assertThat(deserialized.jobId()).isEqualTo(jobId);
    assertThat(deserialized.serviceType()).isEqualTo("ELECTRICAL");
    assertThat(deserialized.scheduledAt()).isNull();
  }

  @Test
  void photoUploaded_appendOnly_oldJsonParsesWithNewNullableField() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();

    // Old payload without uploadedBy field
    String oldJson =
        "{\"event_id\":\""
            + eventId
            + "\","
            + "\"tenant_id\":\""
            + tenantId
            + "\","
            + "\"job_id\":\""
            + jobId
            + "\","
            + "\"photo_id\":\""
            + photoId
            + "\","
            + "\"photo_url\":\"https://s3.example.com/photo.jpg\"}";

    PhotoUploaded deserialized = mapper.readValue(oldJson, PhotoUploaded.class);

    assertThat(deserialized.eventId()).isEqualTo(eventId);
    assertThat(deserialized.photoUrl()).isEqualTo("https://s3.example.com/photo.jpg");
    assertThat(deserialized.uploadedBy()).isNull();
  }

  @Test
  void subscriptionUpdated_appendOnly_oldJsonWithMissingFields_parsesSuccessfully()
      throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();

    // Old payload without effectiveAt
    String oldJson =
        "{\"event_id\":\""
            + eventId
            + "\","
            + "\"tenant_id\":\""
            + tenantId
            + "\","
            + "\"stripe_subscription_id\":\"sub_XYZ\","
            + "\"old_plan\":\"STARTER\","
            + "\"new_plan\":\"PROFESSIONAL\"}";

    SubscriptionUpdated deserialized = mapper.readValue(oldJson, SubscriptionUpdated.class);

    assertThat(deserialized.stripeSubscriptionId()).isEqualTo("sub_XYZ");
    assertThat(deserialized.effectiveAt()).isNull();
  }

  // =========================================================================
  // NON_NULL suppression: null fields must be omitted from serialized JSON
  // =========================================================================

  @Test
  void jobSubmitted_nullField_isOmittedFromJson() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    // scheduledAt is null
    JobSubmitted record = new JobSubmitted(eventId, tenantId, jobId, null, "HVAC", null);
    String json = mapper.writeValueAsString(record);

    assertThat(json).doesNotContain("\"customer_id\"");
    assertThat(json).doesNotContain("\"scheduled_at\"");
    assertThat(json).contains("\"service_type\"");
  }

  @Test
  void photoUploaded_nullField_isOmittedFromJson() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID photoId = UUID.randomUUID();

    PhotoUploaded record =
        new PhotoUploaded(
            eventId, tenantId, jobId, photoId, "https://s3.example.com/photo.jpg", null);
    String json = mapper.writeValueAsString(record);

    assertThat(json).doesNotContain("\"uploaded_by\"");
    assertThat(json).contains("\"photo_url\"");
  }
}
