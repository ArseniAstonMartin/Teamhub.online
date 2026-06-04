package com.teamhub.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Expo Push HTTP V2 provider (PRD §15.2).
 *
 * @param expoPushUrl base URL for the Expo push endpoint
 * @param maxBatchSize maximum number of tokens per single API request (Expo hard limit is 100)
 * @param retryMaxAttempts maximum delivery attempts before giving up
 * @param retryBackoffMs initial backoff delay in milliseconds (doubles on each retry)
 */
@ConfigurationProperties(prefix = "teamhub.push")
public record ExpoPushProperties(
    String expoPushUrl, int maxBatchSize, int retryMaxAttempts, long retryBackoffMs) {}
