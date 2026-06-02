package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Per-field detail for a value auto-filled by an automation rule (PRD §16.3; TASK-052).
 *
 * <p>The {@code ruleId} is exposed so the UI can show "Auto-filled by Rule X" and offer an undo
 * affordance.
 */
public record AutoFilledField(
    @JsonProperty("field_key") String fieldKey,
    Object value,
    @JsonProperty("rule_id") UUID ruleId,
    @JsonProperty("rule_name") String ruleName) {}
