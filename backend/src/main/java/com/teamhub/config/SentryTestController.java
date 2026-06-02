package com.teamhub.config;

import io.sentry.Sentry;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sends a deliberate test exception to Sentry so you can verify the backend project is wired
 * correctly (TASK-012 test step 1).
 *
 * <p>Disabled when {@code SPRING_PROFILES_ACTIVE=prod}. Remove or keep disabled after verification.
 */
@RestController
@RequestMapping({"/internal/sentry", "/api/v1/internal/sentry"})
@Profile("!prod")
public class SentryTestController {

  @PostMapping("/test")
  public ResponseEntity<Map<String, String>> captureTestException() {
    try {
      throw new Exception("This is a test.");
    } catch (Exception e) {
      Sentry.captureException(e);
    }
    return ResponseEntity.ok(
        Map.of(
            "status", "captured",
            "message",
                "Test exception sent to Sentry. Check your java-spring-boot project issues."));
  }
}
