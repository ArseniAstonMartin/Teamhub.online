package com.teamhub.automation;

import com.teamhub.automation.dto.AutomationRuleResponse;
import com.teamhub.automation.dto.CreateAutomationRuleRequest;
import com.teamhub.automation.dto.EvaluateAutomationsRequest;
import com.teamhub.automation.dto.EvaluateAutomationsResponse;
import com.teamhub.automation.dto.UpdateAutomationRuleRequest;
import com.teamhub.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automation-rule CRUD endpoints and the stateless engine evaluation endpoint (PRD §16.2, §16.3,
 * §5.3; TASK-051, TASK-052).
 *
 * <p>CRUD operations require {@code PERM_automation.edit} (ADMIN only). The evaluation endpoint
 * ({@code POST /evaluate}) requires {@code PERM_automation.read}, which is held by both ADMIN and
 * DISPATCHER so that Dispatchers can trigger evaluation when creating jobs (PRD §16.3).
 *
 * <p>Delete is a soft-delete ({@code archived_at = now()}); the row is never hard-deleted so that
 * the engine can reference historical rule states.
 */
@RestController
@RequestMapping("/api/v1/automations")
@Tag(
    name = "automations",
    description = "Automation-rule CRUD and engine evaluation — PRD §16.2, §16.3, §5.3")
public class AutomationRuleController {

  private final AutomationRuleService service;

  private final AutomationEngineService engineService;

  public AutomationRuleController(
      AutomationRuleService service, AutomationEngineService engineService) {
    this.service = service;
    this.engineService = engineService;
  }

  /** Returns all active (non-archived) automation rules for the current tenant. */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_automation.edit')")
  @Operation(
      summary = "List automation rules",
      description = "Returns active rules ordered by priority ascending.")
  public List<AutomationRuleResponse> list() {
    return service.list(TenantContext.get()).stream().map(AutomationRuleResponse::from).toList();
  }

  /**
   * Creates a new automation rule.
   *
   * @param request rule definition; {@code match_rules} and {@code actions} keys are validated
   *     against the tenant's active appointment schema
   * @return {@code 201 Created} with the persisted rule
   * @throws AutomationRuleValidationException (422) when any field key is not in the active schema
   */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_automation.edit')")
  @Operation(
      summary = "Create an automation rule",
      description =
          "Validates field keys against the active schema, then persists the rule. Returns 201.")
  public ResponseEntity<AutomationRuleResponse> create(
      @Valid @RequestBody CreateAutomationRuleRequest request) {
    AutomationRule rule = service.create(TenantContext.get(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(AutomationRuleResponse.from(rule));
  }

  /**
   * Returns a single automation rule by id.
   *
   * @throws AutomationRuleNotFoundException (404) when not found within the tenant
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_automation.edit')")
  @Operation(summary = "Get an automation rule by id")
  public AutomationRuleResponse get(@PathVariable UUID id) {
    return AutomationRuleResponse.from(service.get(TenantContext.get(), id));
  }

  /**
   * Partially updates an automation rule (PATCH semantics).
   *
   * @throws AutomationRuleNotFoundException (404) when not found within the tenant
   * @throws AutomationRuleValidationException (422) when the updated field keys are invalid
   */
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_automation.edit')")
  @Operation(
      summary = "Update an automation rule",
      description = "Applies partial updates; re-validates field keys against the active schema.")
  public AutomationRuleResponse update(
      @PathVariable UUID id, @RequestBody UpdateAutomationRuleRequest request) {
    return AutomationRuleResponse.from(service.update(TenantContext.get(), id, request));
  }

  /**
   * Soft-deletes an automation rule by setting {@code archived_at}.
   *
   * @throws AutomationRuleNotFoundException (404) when not found within the tenant
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_automation.edit')")
  @Operation(
      summary = "Archive (soft-delete) an automation rule",
      description = "Sets archived_at; the row is not hard-deleted.")
  public ResponseEntity<Void> archive(@PathVariable UUID id) {
    service.archive(TenantContext.get(), id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Evaluates all active automation rules against the supplied draft job state and returns a
   * suggested patch (PRD §16.3; TASK-052).
   *
   * <p>This endpoint is stateless — it operates entirely on the {@code draft_fields} payload and
   * does not require a persisted job. Accessible to ADMIN and DISPATCHER via {@code
   * PERM_job.create} (PRD §16.3: the dispatcher triggers evaluation when creating a job).
   *
   * @param request current draft state + set of manually-edited field keys
   * @return 200 with suggested patch and per-field detail including rule ids for undo affordance
   */
  @PostMapping("/evaluate")
  @PreAuthorize("hasAuthority('PERM_job.create')")
  @Operation(
      summary = "Evaluate automation rules against a draft job",
      description =
          "Stateless evaluation: returns suggested_patch for non-manually-set fields. "
              + "Accessible to ADMIN and DISPATCHER (PRD §16.3).")
  public EvaluateAutomationsResponse evaluate(
      @Valid @RequestBody EvaluateAutomationsRequest request) {
    return engineService.evaluate(TenantContext.get(), request);
  }
}
