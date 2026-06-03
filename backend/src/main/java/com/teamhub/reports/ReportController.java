package com.teamhub.reports;

import com.teamhub.reports.dto.AvgTimesResponse;
import com.teamhub.reports.dto.JobCountsResponse;
import com.teamhub.reports.dto.TechnicianRateEntry;
import com.teamhub.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin/Dispatcher report endpoints (PRD §18.1, TASK-067).
 *
 * <ul>
 *   <li>{@code GET /api/v1/reports/jobs} — job counts by period; {@code PERM_report.read}.
 *   <li>{@code GET /api/v1/reports/avg-times} — avg time-to-claim and time-to-completion; {@code
 *       PERM_report.read}.
 *   <li>{@code GET /api/v1/reports/per-technician} — per-technician rates; {@code PERM_report.read}
 *       + Team+ plan check (returns 402 otherwise).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('PERM_report.read')")
@Tag(name = "reports", description = "Admin/Dispatcher reporting endpoints — PRD §18.1")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  /**
   * Job counts grouped by the requested period.
   *
   * @param period one of {@code day} (default), {@code week}, {@code month}, {@code year}
   * @param from start date (inclusive, ISO-8601 date)
   * @param to end date (inclusive, ISO-8601 date)
   */
  @GetMapping("/jobs")
  @Operation(summary = "Job counts by period", description = "PRD §18.1 — Admin and Dispatcher")
  public ResponseEntity<JobCountsResponse> getJobCounts(
      @RequestParam(defaultValue = "day") String period,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    UUID tenantId = TenantContext.get();
    LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(29);
    LocalDate resolvedTo = to != null ? to : LocalDate.now();

    return ResponseEntity.ok(
        reportService.getJobCounts(tenantId, period, resolvedFrom, resolvedTo));
  }

  /**
   * Average time-to-claim and time-to-completion.
   *
   * @param from start date (inclusive, ISO-8601 date)
   * @param to end date (inclusive, ISO-8601 date)
   */
  @GetMapping("/avg-times")
  @Operation(
      summary = "Average times to claim and complete",
      description = "PRD §18.1 — Admin and Dispatcher")
  public ResponseEntity<AvgTimesResponse> getAvgTimes(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    UUID tenantId = TenantContext.get();
    LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(29);
    LocalDate resolvedTo = to != null ? to : LocalDate.now();

    return ResponseEntity.ok(reportService.getAvgTimes(tenantId, resolvedFrom, resolvedTo));
  }

  /**
   * Per-technician completion rates — gated behind Team+ plan (PRD §18.2).
   *
   * @param from start date (inclusive, ISO-8601 date)
   * @param to end date (inclusive, ISO-8601 date)
   */
  @GetMapping("/per-technician")
  @Operation(
      summary = "Per-technician completion rates",
      description = "PRD §18.1 — Admin only; Team+ plan required (402 otherwise)")
  public ResponseEntity<List<TechnicianRateEntry>> getTechnicianRates(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    UUID tenantId = TenantContext.get();
    LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(29);
    LocalDate resolvedTo = to != null ? to : LocalDate.now();

    return ResponseEntity.ok(
        reportService.getTechnicianCompletionRates(tenantId, resolvedFrom, resolvedTo));
  }
}
