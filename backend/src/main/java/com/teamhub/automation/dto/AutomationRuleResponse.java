package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.teamhub.automation.AutomationRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response representation of an {@link AutomationRule} (PRD §16.2; TASK-051).
 *
 * <p>Includes all fields including soft-delete timestamp ({@code archived_at}, {@code null} when
 * the rule is active) so that callers can detect archived rules in edge-case responses.
 */
public record AutomationRuleResponse(
    UUID id,
    @JsonProperty("tenant_id") UUID tenantId,
    String name,
    @JsonProperty("trigger_fields") List<String> triggerFields,
    @JsonProperty("match_rules") Map<String, Object> matchRules,
    Map<String, Object> actions,
    boolean active,
    int priority,
    @JsonProperty("archived_at") Instant archivedAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {

  /** Builds a response from the given {@link AutomationRule} entity. */
  public static AutomationRuleResponse from(AutomationRule rule) {
    return new AutomationRuleResponse(
        rule.getId(),
        rule.getTenantId(),
        rule.getName(),
        rule.getTriggerFields(),
        rule.getMatchRules(),
        rule.getActions(),
        rule.isActive(),
        rule.getPriority(),
        rule.getArchivedAt(),
        rule.getCreatedAt(),
        rule.getUpdatedAt());
  }
}
