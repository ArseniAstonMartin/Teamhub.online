package com.teamhub.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.teamhub.reports.dto.DayBucket;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link ReportRepository} covering defensive type-dispatch branches that are not
 * reachable via a real Postgres + JdbcTemplate integration (TASK-067/068).
 *
 * <p>JdbcTemplate is mocked to return alternative bucket object types ({@link OffsetDateTime} and
 * {@link String}) so that the fallback branches in {@code getJobCountsByPeriod} and {@code
 * pivotJobCountRows} are exercised.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ReportRepositoryUnitTest {

  @Mock private JdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // getJobCountsByPeriod — OffsetDateTime bucket branch
  // ---------------------------------------------------------------------------

  @Test
  void getJobCountsByPeriod_offsetDateTimeBucket_parsedCorrectly() {
    UUID tenantId = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    OffsetDateTime odt = today.atStartOfDay().atOffset(ZoneOffset.UTC);

    Map<String, Object> row = Map.of("bucket", odt, "status", "SUBMITTED", "cnt", 3L);
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

    ReportRepository repo = new ReportRepository(jdbc);
    List<DayBucket> buckets = repo.getJobCountsByPeriod(tenantId, "day", today, today);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).date()).isEqualTo(today);
    assertThat(buckets.get(0).created()).isEqualTo(3L);
  }

  // ---------------------------------------------------------------------------
  // getJobCountsByPeriod — String bucket fallback branch
  // ---------------------------------------------------------------------------

  @Test
  void getJobCountsByPeriod_stringBucket_parsedCorrectly() {
    UUID tenantId = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    String bucketStr = today + " 00:00:00";

    Map<String, Object> row = Map.of("bucket", bucketStr, "status", "COMPLETED", "cnt", 2L);
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

    ReportRepository repo = new ReportRepository(jdbc);
    List<DayBucket> buckets = repo.getJobCountsByPeriod(tenantId, "day", today, today);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).date()).isEqualTo(today);
    assertThat(buckets.get(0).completed()).isEqualTo(2L);
  }

  // ---------------------------------------------------------------------------
  // pivotJobCountRows — Timestamp bucket branch (via mvGetJobCountsByDay)
  // ---------------------------------------------------------------------------

  @Test
  void mvGetJobCountsByDay_timestampBucket_parsedCorrectly() {
    UUID tenantId = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    Timestamp ts = Timestamp.valueOf(today.atStartOfDay());

    Map<String, Object> row = Map.of("bucket", ts, "status", "CANCELED", "cnt", 1L);
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

    ReportRepository repo = new ReportRepository(jdbc);
    List<DayBucket> buckets = repo.mvGetJobCountsByDay(tenantId, today, today);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).date()).isEqualTo(today);
    assertThat(buckets.get(0).cancelled()).isEqualTo(1L);
  }

  // ---------------------------------------------------------------------------
  // pivotJobCountRows — String bucket fallback branch (via mvGetJobCountsByWeek)
  // ---------------------------------------------------------------------------

  @Test
  void mvGetJobCountsByWeek_stringBucket_parsedCorrectly() {
    UUID tenantId = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    String bucketStr = today + " 00:00:00";

    Map<String, Object> row = Map.of("bucket", bucketStr, "status", "SUBMITTED", "cnt", 5L);
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

    ReportRepository repo = new ReportRepository(jdbc);
    List<DayBucket> buckets = repo.mvGetJobCountsByWeek(tenantId, today, today);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).created()).isEqualTo(5L);
  }

  // ---------------------------------------------------------------------------
  // pivotJobCountRows — empty list returns empty result
  // ---------------------------------------------------------------------------

  @Test
  void mvGetJobCountsByMonth_emptyRows_returnsEmptyList() {
    UUID tenantId = UUID.randomUUID();
    LocalDate today = LocalDate.now();

    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    ReportRepository repo = new ReportRepository(jdbc);
    List<DayBucket> buckets = repo.mvGetJobCountsByMonth(tenantId, today, today);

    assertThat(buckets).isEmpty();
  }
}
