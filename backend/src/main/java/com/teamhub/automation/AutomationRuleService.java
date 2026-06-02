package com.teamhub.automation;

import com.teamhub.automation.dto.CreateAutomationRuleRequest;
import com.teamhub.automation.dto.UpdateAutomationRuleRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for automation-rule CRUD operations (PRD §16.2; TASK-051).
 *
 * <p>All mutating operations delegate field-key validation to {@link AutomationRuleValidator}
 * before persisting, so any unknown field key raises {@link AutomationRuleValidationException} (422
 * UNPROCESSABLE_ENTITY).
 *
 * <p>Delete is a soft-delete: {@link #archive} sets {@code archived_at} to the current instant
 * without removing the row, so historical audit and engine references are preserved.
 */
@Service
@Transactional
public class AutomationRuleService {

  private final AutomationRuleRepository repository;

  private final AutomationRuleValidator validator;

  public AutomationRuleService(
      AutomationRuleRepository repository, AutomationRuleValidator validator) {
    this.repository = repository;
    this.validator = validator;
  }

  /**
   * Returns all non-archived rules for the tenant, ordered by {@code priority} ascending then
   * {@code name} ascending (PRD §16.3 — first matching rule in priority order wins).
   */
  @Transactional(readOnly = true)
  public List<AutomationRule> list(UUID tenantId) {
    return repository.findByTenantIdAndArchivedAtIsNullOrderByPriorityAscNameAsc(tenantId);
  }

  /**
   * Returns a single rule scoped to the given tenant.
   *
   * @throws AutomationRuleNotFoundException (404) when {@code id} is not found within the tenant
   */
  @Transactional(readOnly = true)
  public AutomationRule get(UUID tenantId, UUID id) {
    return repository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new AutomationRuleNotFoundException(id));
  }

  /**
   * Creates and persists a new automation rule after validating field-key references.
   *
   * @throws AutomationRuleValidationException (422) when any field key is unknown in the active
   *     schema
   */
  public AutomationRule create(UUID tenantId, CreateAutomationRuleRequest req) {
    validator.validate(tenantId, req.triggerFields(), req.matchRules(), req.actions());

    AutomationRule rule = new AutomationRule();
    rule.setTenantId(tenantId);
    rule.setName(req.name());
    rule.setTriggerFields(req.triggerFields());
    rule.setMatchRules(req.matchRules());
    rule.setActions(req.actions());
    rule.setActive(req.active() != null ? req.active() : true);
    rule.setPriority(req.priority() != null ? req.priority() : 100);

    return repository.save(rule);
  }

  /**
   * Partially updates an existing rule (PATCH semantics). The merged {@code match_rules} and {@code
   * actions} are re-validated before saving.
   *
   * @throws AutomationRuleNotFoundException (404) when the rule is not found within the tenant
   * @throws AutomationRuleValidationException (422) when the updated field keys are invalid
   */
  public AutomationRule update(UUID tenantId, UUID id, UpdateAutomationRuleRequest req) {
    AutomationRule rule = get(tenantId, id);

    List<String> newTriggerFields =
        req.triggerFields() != null ? req.triggerFields() : rule.getTriggerFields();
    Map<String, Object> newMatchRules =
        req.matchRules() != null ? req.matchRules() : rule.getMatchRules();
    Map<String, Object> newActions = req.actions() != null ? req.actions() : rule.getActions();

    validator.validate(tenantId, newTriggerFields, newMatchRules, newActions);

    if (req.name() != null) {
      rule.setName(req.name());
    }
    rule.setTriggerFields(newTriggerFields);
    rule.setMatchRules(newMatchRules);
    rule.setActions(newActions);
    if (req.active() != null) {
      rule.setActive(req.active());
    }
    if (req.priority() != null) {
      rule.setPriority(req.priority());
    }

    return repository.save(rule);
  }

  /**
   * Soft-deletes the rule by setting {@code archived_at} to now.
   *
   * @throws AutomationRuleNotFoundException (404) when the rule is not found within the tenant
   */
  public void archive(UUID tenantId, UUID id) {
    AutomationRule rule = get(tenantId, id);
    rule.setArchivedAt(Instant.now());
    repository.save(rule);
  }
}
