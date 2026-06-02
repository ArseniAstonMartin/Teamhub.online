package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Request body for {@code PATCH /api/v1/automations/{id}} (PRD §16.2; TASK-051).
 *
 * <p>All fields are optional; only non-null values are applied. The merged {@code match_rules} and
 * {@code actions} are re-validated against the active schema after the update.
 */
public record UpdateAutomationRuleRequest(
    String name,
    @JsonProperty("trigger_fields") List<String> triggerFields,
    @JsonProperty("match_rules") Map<String, Object> matchRules,
    Map<String, Object> actions,
    Boolean active,
    Integer priority) {}
