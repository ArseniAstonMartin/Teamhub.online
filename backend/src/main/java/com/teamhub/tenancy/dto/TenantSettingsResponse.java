package com.teamhub.tenancy.dto;

/**
 * Read-only view of tenant-level feature settings (PRD §5.2, §9.3, TASK-053; TASK-128).
 *
 * @param closeJobsEnabled whether Technicians are allowed to mark jobs Completed directly
 * @param timezone IANA time zone used for scheduling display
 */
public record TenantSettingsResponse(boolean closeJobsEnabled, String timezone) {}
