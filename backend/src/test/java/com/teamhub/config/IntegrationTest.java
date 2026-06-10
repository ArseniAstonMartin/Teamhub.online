package com.teamhub.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/**
 * Marks a slow integration test and wires test infrastructure.
 *
 * <p>Locally (default profile): {@link TestcontainersConfiguration} starts Postgres, Redis, and
 * MinIO in Docker.
 *
 * <p>CI ({@code ci} profile): {@link CiTestConfiguration} uses GitHub Actions service containers
 * via {@code SPRING_DATASOURCE_URL}, {@code SPRING_DATA_REDIS_HOST}, and {@code MINIO_ENDPOINT}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@Import({TestcontainersConfiguration.class, CiTestConfiguration.class})
public @interface IntegrationTest {}
