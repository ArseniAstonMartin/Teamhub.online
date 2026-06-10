package com.teamhub.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for in-app alert banners (PRD §28, TASK-118).
 *
 * <p>Alerts are created by {@link com.teamhub.escalation.EscalateUnclaimedJobHandler} and
 * dismissed by the authenticated Admin through {@code POST /api/v1/alerts/{id}/dismiss}.
 */
@Service
@Transactional
public class InAppAlertService {

  private final InAppAlertRepository repo;

  public InAppAlertService(InAppAlertRepository repo) {
    this.repo = repo;
  }

  /**
   * Returns all undismissed alerts for the given user, ordered by persistence default (creation
   * order from the index).
   */
  @Transactional(readOnly = true)
  public List<InAppAlertResponse> getPendingAlerts(UUID userId) {
    return repo.findByUserIdAndDismissedFalse(userId).stream()
        .map(InAppAlertResponse::from)
        .toList();
  }

  /**
   * Marks the alert as dismissed. Throws {@code 404} if the alert does not exist and {@code 403}
   * if the caller is not the alert's owner.
   */
  public void dismiss(UUID alertId, UUID userId) {
    InAppAlert alert =
        repo.findById(alertId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!alert.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    alert.setDismissed(true);
    repo.save(alert);
  }

  /** Creates a {@code JOB_NOT_HANDLED} alert for the given Admin user. */
  public InAppAlert createJobNotHandledAlert(UUID tenantId, UUID userId, UUID jobId) {
    InAppAlert alert = new InAppAlert();
    alert.setTenantId(tenantId);
    alert.setUserId(userId);
    alert.setJobId(jobId);
    alert.setKind("JOB_NOT_HANDLED");
    alert.setDismissed(false);
    alert.setCreatedAt(Instant.now());
    return repo.save(alert);
  }
}
