package com.teamhub.reports;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link MaterializedViewRefreshJob} (TASK-068).
 *
 * <p>Uses a mocked {@link JdbcTemplate} so the scheduler logic can be exercised without a running
 * Postgres instance.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MaterializedViewRefreshJobUnitTest {

  @Mock private JdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // refreshAll — happy path
  // ---------------------------------------------------------------------------

  @Test
  void refreshAll_allViewsSucceed_executesAllSixStatements() {
    MaterializedViewRefreshJob job = new MaterializedViewRefreshJob(jdbc);

    assertThatCode(job::refreshAll).doesNotThrowAnyException();

    verify(jdbc, times(MaterializedViewRefreshJob.VIEWS.size())).execute(anyString());
  }

  // ---------------------------------------------------------------------------
  // refreshAll — per-view exception isolation
  // ---------------------------------------------------------------------------

  /**
   * When one view's refresh throws, {@link MaterializedViewRefreshJob#refreshAll()} must still
   * attempt to refresh all remaining views and must not propagate the exception.
   */
  @Test
  void refreshAll_oneViewFails_othersStillRefreshed() {
    doThrow(new DataAccessException("simulated failure") {})
        .when(jdbc)
        .execute(contains("mv_jobs_daily"));

    MaterializedViewRefreshJob job = new MaterializedViewRefreshJob(jdbc);

    assertThatCode(job::refreshAll).doesNotThrowAnyException();

    verify(jdbc, times(MaterializedViewRefreshJob.VIEWS.size())).execute(anyString());
  }

  // ---------------------------------------------------------------------------
  // schedule — delegates to refreshAll
  // ---------------------------------------------------------------------------

  @Test
  void schedule_delegatesToRefreshAll() {
    MaterializedViewRefreshJob job = new MaterializedViewRefreshJob(jdbc);

    assertThatCode(job::schedule).doesNotThrowAnyException();

    verify(jdbc, times(MaterializedViewRefreshJob.VIEWS.size())).execute(anyString());
  }
}
