package com.teamhub.automation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link AutomationRule} (PRD §16.2; TASK-051). */
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

  /**
   * Returns active (non-archived) rules for the given tenant, ordered by priority ascending then
   * name ascending. Used by the list endpoint and the automation engine (PRD §16.3).
   */
  List<AutomationRule> findByTenantIdAndArchivedAtIsNullOrderByPriorityAscNameAsc(UUID tenantId);

  /**
   * Returns the rule by primary key scoped to the given tenant. Returns empty when the rule does
   * not belong to the tenant (RLS safety net at the service layer).
   */
  Optional<AutomationRule> findByIdAndTenantId(UUID id, UUID tenantId);
}
