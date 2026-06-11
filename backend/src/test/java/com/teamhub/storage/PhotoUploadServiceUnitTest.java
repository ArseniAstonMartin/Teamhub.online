package com.teamhub.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.eventing.OutboxPublisher;
import com.teamhub.job.Job;
import com.teamhub.job.JobNotFoundException;
import com.teamhub.job.JobRepository;
import com.teamhub.storage.dto.ConfirmUploadRequest;
import com.teamhub.storage.dto.UploadUrlResponse;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Unit tests for {@link PhotoUploadService} (TASK-123). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PhotoUploadServiceUnitTest {

  @Mock private S3Presigner s3Presigner;

  @Mock private JobPhotoRepository jobPhotoRepository;

  @Mock private JobRepository jobRepository;

  @Mock private OutboxPublisher outboxPublisher;

  private static final ObjectStorageProperties STORAGE =
      new ObjectStorageProperties(
          "http://localhost:9000", "minioadmin", "minioadmin", "teamhub", "us-east-1", 15);

  private PhotoUploadService service() {
    return new PhotoUploadService(
        s3Presigner, STORAGE, jobPhotoRepository, jobRepository, outboxPublisher);
  }

  @Test
  void generateUploadUrl_unsupportedMimeType_throws() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                service()
                    .generateUploadUrl(
                        tenantId, jobId, UUID.randomUUID(), "application/pdf", "pdf"))
        .isInstanceOf(UnsupportedMimeTypeException.class);
  }

  @Test
  void generateUploadUrl_validMimeType_returnsPresignedUrl() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    PresignedPutObjectRequest presigned = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
    when(presigned.url()).thenReturn(new URL("https://example.com/upload"));
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

    UploadUrlResponse response =
        service().generateUploadUrl(tenantId, jobId, UUID.randomUUID(), "image/jpeg", "jpg");

    assertThat(response.uploadUrl()).isEqualTo("https://example.com/upload");
    assertThat(response.objectKey()).startsWith(tenantId + "/jobs/" + jobId + "/");
    assertThat(response.expiresAt()).isNotNull();
  }

  @Test
  void confirmUpload_fileTooLarge_throws() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    ConfirmUploadRequest request =
        new ConfirmUploadRequest(
            tenantId + "/jobs/" + jobId + "/photo.jpg",
            "image/jpeg",
            21L * 1024 * 1024,
            null,
            null,
            null);

    assertThatThrownBy(() -> service().confirmUpload(tenantId, jobId, UUID.randomUUID(), request))
        .isInstanceOf(PhotoTooLargeException.class);
  }

  @Test
  void confirmUpload_wrongObjectKey_throws() {
    ConfirmUploadRequest request =
        new ConfirmUploadRequest(
            "other-tenant/jobs/other-job/photo.jpg", "image/jpeg", 1024, null, null, null);

    assertThatThrownBy(
            () ->
                service()
                    .confirmUpload(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void confirmUpload_withoutTakenAt_persistsWithNullTakenAt() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String objectKey = tenantId + "/jobs/" + jobId + "/photo.jpg";
    ConfirmUploadRequest request =
        new ConfirmUploadRequest(objectKey, "image/jpeg", 1024, 800, 600, null);

    when(jobPhotoRepository.save(any(JobPhoto.class)))
        .thenAnswer(
            inv -> {
              JobPhoto photo = inv.getArgument(0);
              assertThat(photo.getTakenAt()).isNull();
              return JobPhoto.builder()
                  .id(UUID.randomUUID())
                  .jobId(photo.getJobId())
                  .tenantId(photo.getTenantId())
                  .uploadedBy(photo.getUploadedBy())
                  .objectKey(photo.getObjectKey())
                  .contentType(photo.getContentType())
                  .sizeBytes(photo.getSizeBytes())
                  .width(photo.getWidth())
                  .height(photo.getHeight())
                  .takenAt(photo.getTakenAt())
                  .build();
            });

    var response = service().confirmUpload(tenantId, jobId, userId, request);

    assertThat(response.objectKey()).isEqualTo(objectKey);
    verify(jobPhotoRepository).save(any(JobPhoto.class));
  }

  @Test
  void confirmUpload_validRequest_persistsAndPublishes() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String objectKey = tenantId + "/jobs/" + jobId + "/photo.jpg";
    ConfirmUploadRequest request =
        new ConfirmUploadRequest(
            objectKey, "image/jpeg", 2048, 800, 600, OffsetDateTime.parse("2026-06-10T10:00:00Z"));

    when(jobPhotoRepository.save(any(JobPhoto.class)))
        .thenAnswer(
            inv -> {
              JobPhoto photo = inv.getArgument(0);
              return JobPhoto.builder()
                  .id(UUID.randomUUID())
                  .jobId(photo.getJobId())
                  .tenantId(photo.getTenantId())
                  .uploadedBy(photo.getUploadedBy())
                  .objectKey(photo.getObjectKey())
                  .contentType(photo.getContentType())
                  .sizeBytes(photo.getSizeBytes())
                  .width(photo.getWidth())
                  .height(photo.getHeight())
                  .takenAt(photo.getTakenAt())
                  .build();
            });

    var response = service().confirmUpload(tenantId, jobId, userId, request);

    assertThat(response.objectKey()).isEqualTo(objectKey);
    verify(jobPhotoRepository).save(any(JobPhoto.class));
    verify(outboxPublisher).publish(any(), any(), any(), any());
  }

  @Test
  void listPhotos_missingJob_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().listPhotos(tenantId, jobId))
        .isInstanceOf(JobNotFoundException.class);
  }

  @Test
  void listPhotos_existingPhotos_returnsDownloadUrls() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    Job job = new Job();
    job.setId(jobId);
    job.setTenantId(tenantId);

    JobPhoto photo =
        JobPhoto.builder()
            .id(UUID.randomUUID())
            .jobId(jobId)
            .tenantId(tenantId)
            .uploadedBy(UUID.randomUUID())
            .objectKey(tenantId + "/jobs/" + jobId + "/photo.jpg")
            .contentType("image/jpeg")
            .sizeBytes(1024)
            .build();

    PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
    when(presigned.url()).thenReturn(new URL("https://example.com/download"));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    when(jobRepository.findByIdAndTenantIdAndArchivedAtIsNull(jobId, tenantId))
        .thenReturn(Optional.of(job));
    when(jobPhotoRepository.findByJobIdAndTenantIdOrderByCreatedAtDesc(jobId, tenantId))
        .thenReturn(List.of(photo));

    var responses = service().listPhotos(tenantId, jobId);

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).downloadUrl()).isEqualTo("https://example.com/download");
  }
}
