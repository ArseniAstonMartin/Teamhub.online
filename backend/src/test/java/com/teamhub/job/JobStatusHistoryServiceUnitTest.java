package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.teamhub.job.dto.JobStatusHistoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link JobStatusHistoryService} (TASK-124). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class JobStatusHistoryServiceUnitTest {

  @Mock private JobRepository jobRepository;

  @Mock private JobStatusHistoryRepository historyRepository;

  private JobStatusHistoryService service() {
    return new JobStatusHistoryService(jobRepository, historyRepository);
  }

  @Test
  void listStatusHistory_existingJob_returnsMappedRows() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    Job job = new Job();
    job.setId(jobId);
    job.setTenantId(tenantId);

    JobStatusHistory row =
        JobStatusHistory.builder()
            .jobId(jobId)
            .tenantId(tenantId)
            .fromStatus(JobStatus.SUBMITTED.name())
            .toStatus(JobStatus.ASSIGNED_TO_TECHNICIAN.name())
            .createdAt(Instant.parse("2026-06-10T10:00:00Z"))
            .build();

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));
    when(historyRepository.findByJobIdAndTenantIdOrderByCreatedAtAsc(jobId, tenantId))
        .thenReturn(List.of(row));

    List<JobStatusHistoryResponse> history = service().listStatusHistory(tenantId, jobId);

    assertThat(history).hasSize(1);
    assertThat(history.get(0).fromStatus()).isEqualTo(JobStatus.SUBMITTED.name());
    assertThat(history.get(0).toStatus()).isEqualTo(JobStatus.ASSIGNED_TO_TECHNICIAN.name());
  }

  @Test
  void listStatusHistory_missingJob_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().listStatusHistory(tenantId, jobId))
        .isInstanceOf(JobNotFoundException.class);
  }
}
