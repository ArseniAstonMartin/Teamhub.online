package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response from the automation engine evaluation endpoint (PRD §16.3; TASK-052).
 *
 * <p>{@code suggestedPatch} is the flat map of field key → suggested value for all non-manually-set
 * fields that matched at least one rule. {@code autoFilledFields} provides the same data enriched
 * with the originating rule id and name so the UI can display "Auto-filled by Rule X" badges.
 */
public record EvaluateAutomationsResponse(
    @JsonProperty("suggested_patch") Map<String, Object> suggestedPatch,
    @JsonProperty("auto_filled_fields") List<AutoFilledField> autoFilledFields) {}
