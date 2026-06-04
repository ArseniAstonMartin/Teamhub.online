package com.teamhub.escalation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * JSON payload stored in the {@code delayed_jobs} row for {@code kind='escalate_unclaimed_job'}
 * (PRD §10.3, TASK-046).
 */
public record EscalationPayload(@JsonProperty("jobId") UUID jobId) {}
