package com.teamhub.config;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring {@code @Async} execution and configures a global uncaught-exception handler for
 * async tasks (TASK-028, PRD §19.2).
 *
 * <p>The handler logs audit failures at ERROR level so they are visible in structured logs and do
 * not silently disappear. This keeps the auth response path clean: a transient audit DB failure
 * never propagates to the HTTP caller.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new AuditAsyncUncaughtExceptionHandler();
  }

  private static final class AuditAsyncUncaughtExceptionHandler
      implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
      log.error(
          "Async task failed — method={} params={}: {}",
          method.getDeclaringClass().getSimpleName() + "." + method.getName(),
          params.length,
          ex.getMessage(),
          ex);
      // TO-DO: route to Sentry once TASK-Sentry lands
    }
  }
}
