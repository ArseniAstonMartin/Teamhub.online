package com.teamhub.billing;

import com.teamhub.tenancy.Plan;
import java.util.Map;

/**
 * Central registry of per-plan feature limits (PRD §17.3, TASK-064).
 *
 * <p>All quota / feature-lock checks must derive their limits from this class. No limit constants
 * are scattered across other modules.
 */
public final class PlanCatalog {

  /** Immutable value object holding the numeric and boolean limits for a plan tier. */
  public record PlanLimits(
      int jobsPerMonth, int seats, boolean schemaEditorEnabled, boolean advancedReportsEnabled) {}

  private static final Map<Plan, PlanLimits> LIMITS =
      Map.of(
          Plan.FREE, new PlanLimits(25, 2, false, false),
          Plan.STARTER, new PlanLimits(200, 5, true, false),
          Plan.TEAM, new PlanLimits(1000, 20, true, true),
          Plan.ENTERPRISE, new PlanLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true));

  private PlanCatalog() {}

  /**
   * Returns the {@link PlanLimits} for the given plan code (case-insensitive).
   *
   * @param planCode the string representation of a {@link Plan} enum value (e.g. {@code "FREE"})
   * @throws IllegalArgumentException when {@code planCode} does not map to a known plan
   */
  public static PlanLimits limitsFor(String planCode) {
    Plan plan = Plan.valueOf(planCode.toUpperCase());
    return LIMITS.get(plan);
  }

  /**
   * Returns the {@link PlanLimits} for the given {@link Plan}.
   *
   * @param plan the tenant's subscription plan
   */
  public static PlanLimits limitsFor(Plan plan) {
    return LIMITS.get(plan);
  }
}
