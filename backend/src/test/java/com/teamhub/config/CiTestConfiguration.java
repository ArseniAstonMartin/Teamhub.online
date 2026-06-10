package com.teamhub.config;

import com.teamhub.storage.ObjectStorageProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/**
 * CI test infrastructure using GitHub Actions service containers (profile {@code ci}).
 *
 * <p>Postgres, Redis, and MinIO are provided by workflow {@code services:} blocks — no
 * Testcontainers startup per test class. Stripe and storage properties mirror {@link
 * TestcontainersConfiguration} so integration tests behave the same in CI and locally.
 */
@Profile("ci")
@TestConfiguration(proxyBeanMethods = false)
public class CiTestConfiguration {

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
