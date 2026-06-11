package com.teamhub.job.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.job.Job;
import com.teamhub.job.JobStatus;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JobResponse} enrichment helpers (TASK-130). */
@Tag("unit")
class JobResponseTest {

  @Test
  void from_enrichedResponse_includesCustomerAndAssigneeNames() {
    Job job = sampleJob(JobStatus.ASSIGNED_TO_TECHNICIAN);

    JobResponse response = JobResponse.from(job, "+15035551212", "Acme Plumbing", "Tech One");

    assertThat(response.customerPhone()).isEqualTo("+15035551212");
    assertThat(response.customerName()).isEqualTo("Acme Plumbing");
    assertThat(response.assigneeName()).isEqualTo("Tech One");
  }

  @Test
  void from_submittedJob_masksCustomerPhone() {
    Job job = sampleJob(JobStatus.SUBMITTED);

    JobResponse response = JobResponse.from(job, "+15035551212", "Acme Plumbing", null);

    assertThat(response.customerPhone()).isNull();
    assertThat(response.customerName()).isEqualTo("Acme Plumbing");
  }

  @Test
  void from_withoutEnrichment_leavesNamesNull() {
    Job job = sampleJob(JobStatus.ASSIGNED_TO_TECHNICIAN);

    JobResponse response = JobResponse.from(job);

    assertThat(response.customerName()).isNull();
    assertThat(response.assigneeName()).isNull();
  }

  private static Job sampleJob(JobStatus status) {
    Job job = new Job();
    job.setId(UUID.randomUUID());
    job.setTenantId(UUID.randomUUID());
    job.setCustomerId(UUID.randomUUID());
    job.setStatus(status);
    return job;
  }
}
