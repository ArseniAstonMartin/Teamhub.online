package com.teamhub.automation;

import com.teamhub.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Thrown when an automation rule is not found within the requesting tenant's scope (TASK-051). */
public class AutomationRuleNotFoundException extends DomainException {

  public static final String CODE = "AUTOMATION_RULE_NOT_FOUND";

  public AutomationRuleNotFoundException(UUID id) {
    super(CODE, HttpStatus.NOT_FOUND, "Automation rule not found: " + id);
  }
}
