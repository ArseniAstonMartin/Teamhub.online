package com.teamhub.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wires internal-console configuration beans (TASK-131). */
@Configuration
@EnableConfigurationProperties(InternalProperties.class)
public class InternalConfig {}
