package com.teamhub.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.billing.QuotaEnforcementService;
import com.teamhub.reports.dto.AvgTimesResponse;
import com.teamhub.reports.dto.DayBucket;
import com.teamhub.reports.dto.JobCountsResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link ReportService} covering JsonProcessingException paths and delegation
 * behaviour (TASK-067).
 *
 * <p>All collaborators are mocked so no Testcontainers or Spring context is needed.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ReportServiceUnitTest {

  @Mock private ReportRepository reportRepository;

  @Mock private QuotaEnforcementService quotaEnforcementService;

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOps;

  @Mock private ObjectMapper objectMapper;

  private ReportService service() {
    return new ReportService(
        reportRepository, quotaEnforcementService, redisTemplate, objectMapper);
  }

  // ---------------------------------------------------------------------------
  // Cache hit — deserialization failure falls back to DB
  // ---------------------------------------------------------------------------

  @Test
  void getJobCounts_cacheHit_deserializationFails_fallsBackToDb() throws JsonProcessingException {
    UUID tenantId = UUID.randomUUID();
    LocalDate from = LocalDate.now().withDayOfMonth(1);
    LocalDate to = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1);
    List<DayBucket> dbBuckets = List.of(new DayBucket(from, 5, 3, 1));

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn("{bad json}");
    when(objectMapper.readValue(anyString(), any(TypeReference.class)))
        .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad"));
    when(reportRepository.getJobCountsByPeriod(eq(tenantId), eq("month"), eq(from), eq(to)))
        .thenReturn(dbBuckets);
    when(objectMapper.writeValueAsString(dbBuckets)).thenReturn("[{}]");

    JobCountsResponse response = service().getJobCounts(tenantId, "month", from, to);

    assertThat(response.buckets()).isEqualTo(dbBuckets);
    verify(reportRepository).getJobCountsByPeriod(tenantId, "month", from, to);
  }

  // ---------------------------------------------------------------------------
  // Cache miss — serialization failure does not prevent returning result
  // ---------------------------------------------------------------------------

  @Test
  void getJobCounts_cacheMiss_serializationFails_stillReturnsResult()
      throws JsonProcessingException {
    UUID tenantId = UUID.randomUUID();
    LocalDate from = LocalDate.now().withDayOfMonth(1);
    LocalDate to = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1);
    List<DayBucket> dbBuckets = List.of(new DayBucket(from, 2, 1, 0));

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(reportRepository.getJobCountsByPeriod(eq(tenantId), eq("month"), eq(from), eq(to)))
        .thenReturn(dbBuckets);
    when(objectMapper.writeValueAsString(dbBuckets))
        .thenThrow(
            new com.fasterxml.jackson.core.JsonGenerationException(
                "fail", (com.fasterxml.jackson.core.JsonGenerator) null));

    JobCountsResponse response = service().getJobCounts(tenantId, "month", from, to);

    assertThat(response.buckets()).isEqualTo(dbBuckets);
    verify(valueOps, never()).set(anyString(), anyString(), any());
  }

  // ---------------------------------------------------------------------------
  // Non-month period bypasses cache
  // ---------------------------------------------------------------------------

  @Test
  void getJobCounts_dayPeriod_bypassesCache() throws JsonProcessingException {
    UUID tenantId = UUID.randomUUID();
    LocalDate from = LocalDate.now().minusDays(6);
    LocalDate to = LocalDate.now();
    List<DayBucket> dbBuckets = List.of();

    when(reportRepository.getJobCountsByPeriod(tenantId, "day", from, to)).thenReturn(dbBuckets);

    JobCountsResponse response = service().getJobCounts(tenantId, "day", from, to);

    assertThat(response.buckets()).isEmpty();
    verify(redisTemplate, never()).opsForValue();
  }

  // ---------------------------------------------------------------------------
  // Null period defaults to "day"
  // ---------------------------------------------------------------------------

  @Test
  void getJobCounts_nullPeriod_defaultsToDay() {
    UUID tenantId = UUID.randomUUID();
    LocalDate from = LocalDate.now().minusDays(6);
    LocalDate to = LocalDate.now();

    when(reportRepository.getJobCountsByPeriod(tenantId, "day", from, to)).thenReturn(List.of());

    JobCountsResponse response = service().getJobCounts(tenantId, null, from, to);

    assertThat(response.buckets()).isEmpty();
    verify(reportRepository).getJobCountsByPeriod(tenantId, "day", from, to);
  }

  // ---------------------------------------------------------------------------
  // getAvgTimes — delegates to repository
  // ---------------------------------------------------------------------------

  @Test
  void getAvgTimes_delegatesToRepository() {
    UUID tenantId = UUID.randomUUID();
    LocalDate from = LocalDate.now().minusDays(29);
    LocalDate to = LocalDate.now();
    AvgTimesResponse expected = new AvgTimesResponse(120.0, 3600.0);

    when(reportRepository.getAvgTimes(tenantId, from, to)).thenReturn(expected);

    AvgTimesResponse result = service().getAvgTimes(tenantId, from, to);

    assertThat(result).isEqualTo(expected);
  }
}
