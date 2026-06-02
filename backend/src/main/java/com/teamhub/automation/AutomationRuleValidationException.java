package com.teamhub.automation;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an automation rule references a field key that does not exist in any active
 * appointment schema for the tenant (PRD §16.2; TASK-051).
 */
public class AutomationRuleValidationException extends DomainException {

  public static final String CODE = "AUTOMATION_RULE_INVALID";

  public AutomationRuleValidationException(String message) {
    super(CODE, HttpStatus.UNPROCESSABLE_ENTITY, message);
  }
}
