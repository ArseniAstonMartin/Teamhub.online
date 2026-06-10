package com.teamhub.tenancy.dto;

/**
 * Partial-update payload for tenant feature settings (PRD §5.2, §9.3, TASK-053).
 *
 * <p>Fields are nullable: only non-null values are applied, keeping the endpoint forward-compatible
 * when more settings are added.
 *
 * @param closeJobsEnabled when non-null, sets the tenant's Close Jobs toggle
 * @param timezone when non-null, sets the tenant's IANA time zone (allowed US zones only)
 */
public record UpdateTenantSettingsRequest(Boolean closeJobsEnabled, String timezone) {}
