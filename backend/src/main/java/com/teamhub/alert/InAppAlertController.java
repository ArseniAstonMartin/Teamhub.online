package com.teamhub.alert;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for in-app alert banners (PRD §28, TASK-118).
 *
 * <ul>
 *   <li>{@code GET  /api/v1/alerts}          — pending (undismissed) alerts for the caller
 *   <li>{@code POST /api/v1/alerts/{id}/dismiss} — dismiss a specific alert
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "alerts")
public class InAppAlertController {

  private final InAppAlertService service;

  public InAppAlertController(InAppAlertService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_alert.read')")
  public List<InAppAlertResponse> getPending(@AuthenticationPrincipal Jwt jwt) {
    return service.getPendingAlerts(UUID.fromString(jwt.getSubject()));
  }

  @PostMapping("/{id}/dismiss")
  @PreAuthorize("hasAuthority('PERM_alert.read')")
  public ResponseEntity<Void> dismiss(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    service.dismiss(id, UUID.fromString(jwt.getSubject()));
    return ResponseEntity.noContent().build();
  }
}
