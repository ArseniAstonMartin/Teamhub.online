package com.teamhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Unit tests for {@link SentryConfig#mdcTagsEventProcessor()} MDC tag attachment (TASK-012). */
class SentryConfigTest {

  private EventProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new SentryConfig().mdcTagsEventProcessor();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void process_whenMdcHasAllKeys_attachesTagsAndUser() {
    MDC.put("tenant_id", "tenant-abc");
    MDC.put("user_id", "user-123");
    MDC.put("request_id", "req-456");

    SentryEvent event = new SentryEvent();
    SentryEvent result = processor.process(event, new Hint());

    assertThat(result).isSameAs(event);
    assertThat(result.getTag("tenant_id")).isEqualTo("tenant-abc");
    assertThat(result.getTag("request_id")).isEqualTo("req-456");
    assertThat(result.getUser()).isNotNull();
    assertThat(result.getUser().getId()).isEqualTo("user-123");
  }

  @Test
  void process_whenMdcEmpty_leavesEventUnchanged() {
    SentryEvent event = new SentryEvent();
    SentryEvent result = processor.process(event, new Hint());

    assertThat(result.getTag("tenant_id")).isNull();
    assertThat(result.getTag("request_id")).isNull();
    assertThat(result.getUser()).isNull();
  }

  @Test
  void process_whenMdcHasOnlyTenantId_attachesTenantTagOnly() {
    MDC.put("tenant_id", "tenant-only");

    SentryEvent event = new SentryEvent();
    processor.process(event, new Hint());

    assertThat(event.getTag("tenant_id")).isEqualTo("tenant-only");
    assertThat(event.getTag("request_id")).isNull();
    assertThat(event.getUser()).isNull();
  }

  @Test
  void process_whenMdcHasOnlyRequestId_attachesRequestIdTagOnly() {
    MDC.put("request_id", "req-only");

    SentryEvent event = new SentryEvent();
    processor.process(event, new Hint());

    assertThat(event.getTag("request_id")).isEqualTo("req-only");
    assertThat(event.getTag("tenant_id")).isNull();
    assertThat(event.getUser()).isNull();
  }

  @Test
  void process_whenMdcHasOnlyUserId_attachesUserOnly() {
    MDC.put("user_id", "user-only");

    SentryEvent event = new SentryEvent();
    processor.process(event, new Hint());

    assertThat(event.getUser()).isNotNull();
    assertThat(event.getUser().getId()).isEqualTo("user-only");
    assertThat(event.getTag("tenant_id")).isNull();
    assertThat(event.getTag("request_id")).isNull();
  }
}
