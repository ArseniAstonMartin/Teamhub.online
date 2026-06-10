package com.teamhub.escalation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.alert.InAppAlertService;
import com.teamhub.eventing.DelayedJob;
import com.teamhub.eventing.DelayedJobHandler;
import com.teamhub.job.Job;
import com.teamhub.job.JobRepository;
import com.teamhub.job.JobStatus;
import com.teamhub.push.DeviceToken;
import com.teamhub.push.DeviceTokenRepository;
import com.teamhub.push.ExpoPushClient;
import com.teamhub.push.PushMessage;
import com.teamhub.push.PushTicket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for delayed jobs with {@code kind='escalate_unclaimed_job'} (PRD §10.3, TASK-046).
 *
 * <p>Re-reads the job from the database: if the job is still in {@code SUBMITTED} status (i.e. no
 * technician has claimed it), pushes a {@code "JOB NOT HANDLED"} notification to every Admin of the
 * tenant via Expo Push. If the job has already been claimed the handler exits silently.
 *
 * <p>This handler is registered as a Spring {@link Component} so that {@link
 * com.teamhub.eventing.DelayedJobDispatcher} can discover it at startup via the {@link
 * DelayedJobHandler} interface.
 */
@Component
public class EscalateUnclaimedJobHandler implements DelayedJobHandler {

  private static final Logger log = LoggerFactory.getLogger(EscalateUnclaimedJobHandler.class);

  static final String KIND = "escalate_unclaimed_job";

  static final String PUSH_TITLE = "Job Not Handled";

  static final String PUSH_BODY = "JOB NOT HANDLED";

  private final JobRepository jobRepository;

  private final DeviceTokenRepository deviceTokenRepository;

  private final ExpoPushClient expoPushClient;

  private final ObjectMapper objectMapper;

  private final InAppAlertService alertService;

  public EscalateUnclaimedJobHandler(
      JobRepository jobRepository,
      DeviceTokenRepository deviceTokenRepository,
      ExpoPushClient expoPushClient,
      ObjectMapper objectMapper,
      InAppAlertService alertService) {
    this.jobRepository = jobRepository;
    this.deviceTokenRepository = deviceTokenRepository;
    this.expoPushClient = expoPushClient;
    this.objectMapper = objectMapper;
    this.alertService = alertService;
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public void handle(DelayedJob job) {
    EscalationPayload payload;
    try {
      payload = objectMapper.readValue(job.getPayload(), EscalationPayload.class);
    } catch (Exception e) {
      log.error(
          "escalate-unclaimed-job: failed to parse payload for delayed job id={}: {}",
          job.getId(),
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to parse escalation payload", e);
    }

    UUID jobId = payload.jobId();
    UUID tenantId = job.getTenantId();

    Optional<Job> jobOpt = jobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      log.warn("escalate-unclaimed-job: job id={} not found in DB; skipping escalation", jobId);
      return;
    }

    Job fieldJob = jobOpt.get();
    if (fieldJob.getStatus() != JobStatus.SUBMITTED) {
      log.info(
          "escalate-unclaimed-job: job id={} is in status {}; no escalation needed",
          jobId,
          fieldJob.getStatus());
      return;
    }

    List<DeviceToken> adminTokens =
        deviceTokenRepository.findByTenantIdAndUserRoleAndStaleIsFalse(tenantId, "ADMIN");

    if (adminTokens.isEmpty()) {
      log.warn(
          "escalate-unclaimed-job: no active ADMIN tokens for tenant={} jobId={}; push skipped",
          tenantId,
          jobId);
      return;
    }

    List<PushMessage> messages =
        adminTokens.stream()
            .map(
                token ->
                    new PushMessage(
                        token.getExpoToken(),
                        PUSH_TITLE,
                        PUSH_BODY,
                        Map.of("jobId", jobId.toString(), "screen", "JobDetail"),
                        "jobs"))
            .collect(Collectors.toList());

    List<PushTicket> tickets = expoPushClient.sendBatch(messages);

    log.info(
        "escalate-unclaimed-job: sent {} escalation push(es) for jobId={} tenantId={},"
            + " received {} tickets",
        messages.size(),
        jobId,
        tenantId,
        tickets.size());

    // Also create in-app alerts for admins who may not have a push token registered
    // TO-DO: also alert Admins without push tokens by querying the users table directly — PRD §28
    adminTokens.stream()
        .map(DeviceToken::getUserId)
        .distinct()
        .forEach(
            adminUserId -> alertService.createJobNotHandledAlert(tenantId, adminUserId, jobId));
  }
}
