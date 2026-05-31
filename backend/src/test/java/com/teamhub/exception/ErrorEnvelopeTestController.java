package com.teamhub.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used to exercise {@link GlobalExceptionHandler} end-to-end through the real
 * Spring MVC stack (TASK-025 test steps 1–3).
 *
 * <p>This class lives under {@code src/test} and is registered explicitly via {@code @Import} in
 * {@link GlobalExceptionHandlerIT}; it is never part of the production component scan.
 */
@RestController
@RequestMapping("/test/envelope")
public class ErrorEnvelopeTestController {

  /** Minimal DTO with validation constraints — used for test Step 1. */
  public record ValidatedRequest(@NotBlank String requiredField, @NotBlank String anotherField) {}

  /**
   * Simulated domain exception used to verify test Step 2.
   *
   * <p>In production, job-domain exceptions will live in {@code com.teamhub.jobs}. This inner class
   * is intentionally test-scoped.
   */
  static class JobAlreadyClaimedException extends DomainException {

    static final String CODE = "JOB_ALREADY_CLAIMED";

    JobAlreadyClaimedException() {
      super(CODE, HttpStatus.CONFLICT, "This job has already been claimed by another technician.");
    }
  }

  /** Step 1: POST with an invalid body triggers {@link MethodArgumentNotValidException} → 422. */
  @PostMapping("/validate")
  public String validate(@Valid @RequestBody ValidatedRequest request) {
    return "ok:" + request.requiredField();
  }

  /** Step 2: throws a {@link DomainException} subclass with code {@code JOB_ALREADY_CLAIMED}. */
  @GetMapping("/domain-error")
  public String domainError() {
    throw new JobAlreadyClaimedException();
  }

  /** Step 3: throws an unhandled {@link NullPointerException} to trigger the 500 path. */
  @GetMapping("/npe")
  public String forcedNpe() {
    String s = null;
    return s.toUpperCase();
  }
}
