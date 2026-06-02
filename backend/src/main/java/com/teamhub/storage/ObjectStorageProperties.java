package com.teamhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("teamhub.storage")
public record ObjectStorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String region,
    int presignedUrlTtlMinutes) {}
