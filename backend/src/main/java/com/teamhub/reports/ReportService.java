package com.teamhub.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.reports.dto.AvgTimesResponse;
import com.teamhub.reports.dto.DayBucket;
import com.teamhub.reports.dto.JobCountsResponse;
import com.teamhub.reports.dto.TechnicianRateEntry;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Report service — thin orchestration layer over {@link ReportRepository} with Redis caching for
 * top-level monthly totals (PRD §18.2, TASK-067).
 *
 * <p>Monthly job counts are cached in Redis at key {@code reports:totals:<tenantId>:<yearMonth>}
 * with a 5-minute TTL. All other queries bypass the cache and hit Postgres directly.
 */
@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private static final java.time.Duration CACHE_TTL = java.time.Duration.ofMinutes(5);

  private final ReportRepository reportRepository;

  private final QuotaEnforcementService quotaEnforcementService;

  private final StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper;

  public ReportService(
      ReportRepository reportRepository,
      QuotaEnforcementService quotaEnforcementService,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper) {
    this.reportRepository = reportRepository;
    this.quotaEnforcementService = quotaEnforcementService;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Returns job counts grouped by the requested period.
   *
   * <p>Monthly granularity results are served from Redis when cached; a cache miss triggers a DB
   * query and populates the cache.
   *
   * @param tenantId the owning tenant
   * @param period one of {@code day}, {@code week}, {@code month}, {@code year}
   * @param from inclusive start date
   * @param to inclusive end date
   */
  public JobCountsResponse getJobCounts(
      UUID tenantId, String period, LocalDate from, LocalDate to) {

    if ("month".equals(period) && from != null && to != null) {
      YearMonth ym = YearMonth.from(from);
      String cacheKey = "reports:totals:" + tenantId + ":" + ym;
      String cached = redisTemplate.opsForValue().get(cacheKey);
      if (cached != null) {
        try {
          List<DayBucket> buckets =
              objectMapper.readValue(cached, new TypeReference<List<DayBucket>>() {});
          return new JobCountsResponse(buckets);
        } catch (JsonProcessingException e) {
          log.warn(
              "Failed to deserialize cached report for key {}; falling through to DB", cacheKey, e);
        }
      }

      List<DayBucket> buckets = reportRepository.getJobCountsByPeriod(tenantId, period, from, to);
      try {
        redisTemplate
            .opsForValue()
            .set(cacheKey, objectMapper.writeValueAsString(buckets), CACHE_TTL);
      } catch (JsonProcessingException e) {
        log.warn(
            "Failed to serialize report for Redis key {}; cache miss will occur next call",
            cacheKey,
            e);
      }
      return new JobCountsResponse(buckets);
    }

    List<DayBucket> buckets =
        reportRepository.getJobCountsByPeriod(tenantId, period == null ? "day" : period, from, to);
    return new JobCountsResponse(buckets);
  }

  /**
   * Returns average time-to-claim and time-to-completion for the given date range.
   *
   * @param tenantId the owning tenant
   * @param from inclusive start date
   * @param to inclusive end date
   */
  public AvgTimesResponse getAvgTimes(UUID tenantId, LocalDate from, LocalDate to) {
    return reportRepository.getAvgTimes(tenantId, from, to);
  }

  /**
   * Returns per-technician completion rates. Requires Team+ plan (PRD §18.2).
   *
   * @param tenantId the owning tenant
   * @param from inclusive start date
   * @param to inclusive end date
   * @throws com.teamhub.billing.PlanFeatureLockedException when the tenant is not on Team+ plan
   */
  public List<TechnicianRateEntry> getTechnicianCompletionRates(
      UUID tenantId, LocalDate from, LocalDate to) {
    quotaEnforcementService.checkAdvancedReports(tenantId);
    return reportRepository.getTechnicianCompletionRates(tenantId, from, to);
  }
}
