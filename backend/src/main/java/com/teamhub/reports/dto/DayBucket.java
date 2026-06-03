package com.teamhub.reports.dto;

import java.time.LocalDate;

/** One time-bucket of job counts for the reports API (PRD §18.1, TASK-067). */
public record DayBucket(LocalDate date, long created, long completed, long cancelled) {}
