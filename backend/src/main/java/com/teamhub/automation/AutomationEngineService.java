package com.teamhub.automation;

import com.teamhub.automation.dto.AutoFilledField;
import com.teamhub.automation.dto.EvaluateAutomationsRequest;
import com.teamhub.automation.dto.EvaluateAutomationsResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automation engine that evaluates active rules against a draft job's current field values (PRD
 * §16.3; TASK-052).
 *
 * <p>Algorithm (PRD §16.3):
 *
 * <ol>
 *   <li>Load all active, non-archived {@link AutomationRule} records for the tenant ordered by
 *       {@code priority ASC} (lower number = higher priority).
 *   <li>For each rule in priority order:
 *       <ul>
 *         <li>Check that at least one field key in {@code trigger_fields} is present in {@code
 *             draftFields}.
 *         <li>Evaluate every entry in {@code match_rules} against {@code draftFields}: string
 *             equality for scalars; for a range value like {@code "2008-2014"} the draft value is
 *             checked with {@link #matchesRangeOrEquality}.
 *         <li>If the rule matches, iterate its {@code actions}: skip fields in {@code
 *             manuallySetFields}; skip fields already in {@code suggestedPatch} (first-match-wins
 *             on overlapping fields, but subsequent rules can fill <em>different</em> fields).
 *       </ul>
 *   <li>Return {@link EvaluateAutomationsResponse} with the accumulated patch and per-field detail.
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class AutomationEngineService {

  private final AutomationRuleRepository repository;

  public AutomationEngineService(AutomationRuleRepository repository) {
    this.repository = repository;
  }

  /**
   * Evaluates all active rules for the tenant against the supplied draft state and returns a
   * suggested patch for fields the user has not manually edited.
   *
   * @param tenantId tenant scope for rule loading
   * @param request current draft state + set of manually-edited field keys
   * @return suggested patch and per-field detail
   */
  public EvaluateAutomationsResponse evaluate(UUID tenantId, EvaluateAutomationsRequest request) {
    Map<String, Object> draftFields = request.draftFields();
    Set<String> manuallySet = request.manuallySetFields();

    List<AutomationRule> rules =
        repository.findByTenantIdAndArchivedAtIsNullOrderByPriorityAscNameAsc(tenantId);

    Map<String, Object> suggestedPatch = new LinkedHashMap<>();
    List<AutoFilledField> autoFilledFields = new ArrayList<>();

    for (AutomationRule rule : rules) {
      if (!rule.isActive()) {
        continue;
      }
      if (!triggerMatches(rule, draftFields)) {
        continue;
      }
      if (!matchRulesMatch(rule, draftFields)) {
        continue;
      }

      Map<String, Object> actions = rule.getActions();
      if (actions == null) {
        continue;
      }
      for (Map.Entry<String, Object> action : actions.entrySet()) {
        String fieldKey = action.getKey();
        if (manuallySet.contains(fieldKey)) {
          continue;
        }
        if (suggestedPatch.containsKey(fieldKey)) {
          continue;
        }
        suggestedPatch.put(fieldKey, action.getValue());
        autoFilledFields.add(
            new AutoFilledField(fieldKey, action.getValue(), rule.getId(), rule.getName()));
      }
    }

    return new EvaluateAutomationsResponse(suggestedPatch, autoFilledFields);
  }

  /**
   * Returns {@code true} when at least one key in the rule's {@code trigger_fields} list is present
   * as a key in {@code draftFields}.
   */
  private boolean triggerMatches(AutomationRule rule, Map<String, Object> draftFields) {
    List<String> triggerFields = rule.getTriggerFields();
    if (triggerFields == null || triggerFields.isEmpty()) {
      return true;
    }
    return triggerFields.stream().anyMatch(draftFields::containsKey);
  }

  /**
   * Returns {@code true} when every entry in the rule's {@code match_rules} map satisfies the
   * corresponding value in {@code draftFields}.
   *
   * <p>Matching semantics (PRD §16.3):
   *
   * <ul>
   *   <li>A match-rules key ending in {@code _range} (e.g. {@code year_range}) is treated as a
   *       range constraint on the base field (e.g. {@code year}). The expected value must be a
   *       string like {@code "YYYY-YYYY"} and the draft value is parsed as an integer year that
   *       must fall within the inclusive range.
   *   <li>Otherwise a case-insensitive string equality check is performed.
   * </ul>
   */
  private boolean matchRulesMatch(AutomationRule rule, Map<String, Object> draftFields) {
    Map<String, Object> matchRules = rule.getMatchRules();
    if (matchRules == null || matchRules.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Object> entry : matchRules.entrySet()) {
      String matchKey = entry.getKey();
      Object expected = entry.getValue();
      // "year_range" → look up "year" in draft; allows range matching via "_range" suffix.
      String draftKey =
          matchKey.endsWith("_range") ? matchKey.substring(0, matchKey.length() - 6) : matchKey;
      Object actual = draftFields.get(draftKey);
      if (actual == null) {
        return false;
      }
      if (!matchesRangeOrEquality(expected, actual)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether {@code actual} satisfies the {@code expected} constraint.
   *
   * <p>If {@code expected} is a string that looks like {@code "YYYY-YYYY"} (a numeric range), the
   * {@code actual} value is parsed as an integer and checked for inclusive membership. Otherwise,
   * {@link Object#toString()} equality (case-insensitive) is used.
   */
  private boolean matchesRangeOrEquality(Object expected, Object actual) {
    if (expected instanceof String expectedStr) {
      if (expectedStr.matches("\\d{4}-\\d{4}")) {
        String[] parts = expectedStr.split("-");
        int lo = Integer.parseInt(parts[0]);
        int hi = Integer.parseInt(parts[1]);
        try {
          int actualYear = Integer.parseInt(actual.toString().trim());
          return actualYear >= lo && actualYear <= hi;
        } catch (NumberFormatException e) {
          return false;
        }
      }
      return expectedStr.equalsIgnoreCase(actual.toString());
    }
    return expected.equals(actual);
  }
}
