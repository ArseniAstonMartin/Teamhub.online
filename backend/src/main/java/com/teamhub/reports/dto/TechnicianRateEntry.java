package com.teamhub.reports.dto;

import java.util.UUID;

/** Per-technician completion-rate entry — Team+ gated (PRD §18.1, TASK-067). */
public record TechnicianRateEntry(
    UUID technicianId, long totalAssigned, long totalCompleted, double completionRate) {}
