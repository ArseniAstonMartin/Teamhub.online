package com.teamhub.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.shared.TenantContext;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a domain event to the transactional outbox (PRD §22.5.3).
 *
 * <p>The event row is persisted inside the caller's active transaction using the same JDBC
 * connection, guaranteeing atomicity: if the surrounding business transaction rolls back, the
 * outbox row is also absent.
 *
 * <p>After the INSERT a best-effort {@code pg_notify('outbox_inserted', '')} is issued so that a
 * long-polling relay can wake immediately instead of waiting for its next scan interval. A failure
 * of this notification never prevents the outbox row from being committed.
 */
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  /** PascalCase: first character uppercase, remaining characters letters only. */
  private static final Pattern PASCAL_CASE = Pattern.compile("[A-Z][A-Za-z]*");

  private final OutboxEventRepository repository;

  private final JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper;

  public OutboxPublisher(
      OutboxEventRepository repository, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.repository = repository;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Persists {@code payloadPojo} as a JSONB outbox event row inside the caller's active
   * transaction.
   *
   * <p>The {@code eventType} must follow PascalCase (e.g. {@code JobSubmitted}, {@code
   * CustomerCreated}): it must start with an uppercase letter followed by zero or more letters.
   * This mirrors the Jackson naming convention used by event consumers and ensures consistent
   * event-type discrimination in downstream systems.
   *
   * <p>After the INSERT, {@code pg_notify('outbox_inserted', '')} is sent on the same connection.
   * The notification is delivered to listening sessions when the surrounding transaction commits.
   * Any exception from {@code pg_notify} is caught and logged at WARN level; the outbox row is
   * never lost due to a notification failure.
   *
   * @param eventType PascalCase event type name (e.g. {@code "JobSubmitted"})
   * @param aggregateType the aggregate root type (e.g. {@code "Job"}, {@code "Customer"})
   * @param aggregateId UUID of the aggregate root
   * @param payloadPojo POJO to serialize to JSONB; must be Jackson-serializable
   * @throws IllegalArgumentException if {@code eventType} does not match {@code [A-Z][A-Za-z]*}
   * @throws IllegalArgumentException if {@code payloadPojo} cannot be serialized to JSON
   * @throws IllegalStateException if the current thread has no tenant context
   * @throws org.springframework.transaction.IllegalTransactionStateException if called outside an
   *     active transaction (propagation MANDATORY enforcement)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(
      String eventType, String aggregateType, UUID aggregateId, Object payloadPojo) {

    if (eventType == null || !PASCAL_CASE.matcher(eventType).matches()) {
      throw new IllegalArgumentException(
          "eventType must be PascalCase (match [A-Z][A-Za-z]*), got: " + eventType);
    }

    UUID tenantId = TenantContext.get();
    if (tenantId == null) {
      throw new IllegalStateException(
          "No tenant context is set; call TenantContext.set(tenantId) before publishing events");
    }

    String json;
    try {
      json = objectMapper.writeValueAsString(payloadPojo);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize payload to JSON", e);
    }

    repository.save(
        OutboxEvent.builder()
            .tenantId(tenantId)
            .eventType(eventType)
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .payload(json)
            .build());

    try {
      jdbcTemplate.execute("SELECT pg_notify('outbox_inserted', '')");
    } catch (Exception e) {
      log.warn(
          "pg_notify('outbox_inserted') failed — notification skipped, outbox row will still"
              + " commit",
          e);
    }
  }
}
