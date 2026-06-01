package com.teamhub.config;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.User;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Attaches MDC context (tenant_id, user_id, request_id) as Sentry tags and user info on every
 * event so that errors can be correlated across tenants and requests in the Sentry dashboard.
 *
 * <p>MDC keys are populated by the request-scoped filter defined in TASK-013. Until that filter
 * exists the tags will be absent but the event will still be captured.
 *
 * <p>TO-DO: human must provision Sentry projects per PRD §26.2 before enabling in production.
 */
@Configuration
public class SentryConfig {

  @Bean
  public EventProcessor mdcTagsEventProcessor() {
    return new EventProcessor() {
      @Override
      public SentryEvent process(SentryEvent event, Hint hint) {
        String tenantId = MDC.get("tenant_id");
        String userId = MDC.get("user_id");
        String requestId = MDC.get("request_id");

        if (tenantId != null) {
          event.setTag("tenant_id", tenantId);
        }
        if (requestId != null) {
          event.setTag("request_id", requestId);
        }
        if (userId != null) {
          User sentryUser = new User();
          sentryUser.setId(userId);
          event.setUser(sentryUser);
        }
        return event;
      }
    };
  }
}
