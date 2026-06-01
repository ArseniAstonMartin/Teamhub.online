package com.teamhub.eventing;

import java.util.UUID;

/**
 * Immutable envelope carrying a single domain event from a Redis Stream to an {@link
 * AbstractOutboxConsumer} handler (PRD §22.5.6).
 *
 * @param id the event UUID (matches {@code outbox_events.id})
 * @param tenantId the owning tenant
 * @param payload JSON payload serialised by {@link OutboxPublisher}
 * @param eventType PascalCase event type (e.g. {@code "JobSubmitted"})
 */
public record EventEnvelope(UUID id, UUID tenantId, String payload, String eventType) {}
