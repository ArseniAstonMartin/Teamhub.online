package com.teamhub.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.teamhub.config.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;

/**
 * Smoke test confirming that the {@link S3Client} bean is wired to the Testcontainers MinIO
 * instance and that the {@code teamhub} bucket was created during context initialisation (TASK-049,
 * TASK-050).
 */
@SpringBootTest
@IntegrationTest
class ObjectStorageConfigIT {

  @Autowired S3Client s3Client;

  @Test
  void s3ClientListBucketsDoesNotThrow() {
    assertThatCode(() -> s3Client.listBuckets()).doesNotThrowAnyException();
  }

  @Test
  void teamhubBucketExists() {
    var response = s3Client.listBuckets();
    assertThat(response.buckets()).extracting(Bucket::name).contains("teamhub");
  }
}
