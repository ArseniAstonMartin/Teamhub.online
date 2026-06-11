package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.job.dto.CancelJobRequest;
import com.teamhub.job.dto.JobResponse;
import com.teamhub.job.dto.JobStatusHistoryResponse;
import com.teamhub.shared.TenantContext;
import com.teamhub.storage.MultipartUploadService;
import com.teamhub.storage.PhotoUploadService;
import com.teamhub.storage.dto.JobPhotoResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for new {@link JobController} endpoints (TASK-123/124/127). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class JobControllerUnitTest {

  @Mock private JobService jobService;

  @Mock private JobNoteService jobNoteService;

  @Mock private JobStatusHistoryService jobStatusHistoryService;

  @Mock private PhotoUploadService photoUploadService;

  @Mock private MultipartUploadService multipartUploadService;

  private JobController controller() {
    return new JobController(
        jobService,
        jobNoteService,
        jobStatusHistoryService,
        photoUploadService,
        multipartUploadService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void cancel_delegatesToJobService() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(tenantId);
    JobResponse expected = sampleJobResponse(jobId, tenantId);
    when(jobService.cancelJob(
            tenantId, jobId, CancellationReason.TOO_EXPENSIVE, actorId, "DISPATCHER"))
        .thenReturn(expected);

    JobResponse response =
        controller()
            .cancel(
                jobId,
                new CancelJobRequest(CancellationReason.TOO_EXPENSIVE),
                jwtAuth(actorId, "DISPATCHER"));

    assertThat(response).isEqualTo(expected);
  }

  @Test
  void reopen_delegatesToJobService() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(tenantId);
    JobResponse expected = sampleJobResponse(jobId, tenantId);
    when(jobService.reopenJob(tenantId, jobId, actorId, "ADMIN")).thenReturn(expected);

    JobResponse response = controller().reopen(jobId, jwtAuth(actorId, "ADMIN"));

    assertThat(response).isEqualTo(expected);
    verify(jobService).reopenJob(tenantId, jobId, actorId, "ADMIN");
  }

  @Test
  void listStatusHistory_delegatesToHistoryService() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    TenantContext.set(tenantId);
    List<JobStatusHistoryResponse> history =
        List.of(
            new JobStatusHistoryResponse(
                JobStatus.SUBMITTED.name(),
                JobStatus.CANCELED.name(),
                "DISPATCHER",
                null,
                Instant.parse("2026-06-10T10:00:00Z")));
    when(jobStatusHistoryService.listStatusHistory(tenantId, jobId)).thenReturn(history);

    assertThat(controller().listStatusHistory(jobId)).isEqualTo(history);
  }

  @Test
  void listPhotos_delegatesToPhotoUploadService() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    TenantContext.set(tenantId);
    List<JobPhotoResponse> photos =
        List.of(
            new JobPhotoResponse(
                UUID.randomUUID(),
                jobId,
                tenantId + "/jobs/" + jobId + "/photo.jpg",
                "image/jpeg",
                1024,
                Instant.parse("2026-06-10T10:00:00Z"),
                "https://example.com/download"));
    when(photoUploadService.listPhotos(tenantId, jobId)).thenReturn(photos);

    assertThat(controller().listPhotos(jobId)).isEqualTo(photos);
  }

  private static JwtAuthenticationToken jwtAuth(UUID userId, String role) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("role", role)
            .build();
    return new JwtAuthenticationToken(jwt);
  }

  private static JobResponse sampleJobResponse(UUID jobId, UUID tenantId) {
    Job job = new Job();
    job.setId(jobId);
    job.setTenantId(tenantId);
    job.setCustomerId(UUID.randomUUID());
    job.setStatus(JobStatus.CANCELED);
    job.setCancellationReason(CancellationReason.OTHER);
    return JobResponse.from(job);
  }
}
