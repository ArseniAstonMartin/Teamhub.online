package com.teamhub.automation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;

/**
 * Request body for the automation engine evaluation endpoint (PRD §16.3; TASK-052).
 *
 * <p>{@code draftFields} carries the current state of the draft job form. {@code manuallySetFields}
 * is the set of field keys the user has explicitly edited; the engine must never overwrite those.
 */
public record EvaluateAutomationsRequest(
    @NotNull @JsonProperty("draft_fields") Map<String, Object> draftFields,
    @JsonProperty("manually_set_fields") Set<String> manuallySetFields) {

  public EvaluateAutomationsRequest {
    if (manuallySetFields == null) {
      manuallySetFields = Set.of();
    }
  }
}
