package com.teamhub.config;

import com.teamhub.storage.ObjectStorageProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

@Profile("!ci")
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("teamhub")
        .withUsername("postgres")
        .withPassword("postgres");
  }

  @Bean
  @ServiceConnection("redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }

  @Bean
  GenericContainer<?> minioContainer() {
    return new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
        .withCommand("server", "/data", "--console-address", ":9001")
        .withExposedPorts(9000)
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        .waitingFor(Wait.forLogMessage(".*API:.*", 1).withStartupTimeout(Duration.ofSeconds(120)));
  }

  @Bean
  DynamicPropertyRegistrar minioPropertyRegistrar(
      @Qualifier("minioContainer") GenericContainer<?> minioContainer) {
    return registry ->
        registry.add(
            "teamhub.storage.endpoint",
            () ->
                "http://%s:%d"
                    .formatted(minioContainer.getHost(), minioContainer.getMappedPort(9000)));
  }

  @Bean
  DynamicPropertyRegistrar stripeTestPropertyRegistrar() {
    return registry -> {
      registry.add("teamhub.stripe.secret-key", () -> "rk_test_placeholder");
      registry.add("teamhub.stripe.price-free", () -> "price_test_free");
      registry.add("teamhub.stripe.price-starter", () -> "price_test_starter");
      registry.add("teamhub.stripe.price-team", () -> "price_test_team");
      registry.add("teamhub.stripe.price-enterprise", () -> "price_test_enterprise");
      registry.add("teamhub.stripe.webhook-secret", () -> "whsec_test_placeholder");
    };
  }

  @Bean
  ApplicationRunner minioInitializer(S3Client s3Client, ObjectStorageProperties storageProperties) {
    return args -> {
      try {
        s3Client.createBucket(r -> r.bucket(storageProperties.bucket()));
      } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
        // Bucket already exists — nothing to do.
      }
    };
  }
}
