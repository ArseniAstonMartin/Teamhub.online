package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /api/v1/automations} (PRD §16.2; TASK-051).
 *
 * <p>{@code match_rules} and {@code actions} are free-form JSONB maps whose keys must correspond to
 * field keys in the tenant's active appointment schema (validated by {@link
 * com.teamhub.automation.AutomationRuleValidator}).
 */
public record CreateAutomationRuleRequest(
    @NotBlank String name,
    @JsonProperty("trigger_fields") List<String> triggerFields,
    @JsonProperty("match_rules") Map<String, Object> matchRules,
    Map<String, Object> actions,
    Boolean active,
    Integer priority) {}
