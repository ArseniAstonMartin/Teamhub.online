package com.teamhub.compliance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TO-DO: privacy policy at https://teamhub.online/privacy should reference the CCPA data export
// endpoint (/api/v1/compliance/export) and erasure endpoint
// (/api/v1/compliance/erase-customer/{id}) per PRD §24.5.

/**
 * CCPA compliance endpoints — Admin-only data export and customer erasure (PRD §24.5, TASK-071).
 *
 * <p>Both actions are guarded by dedicated permissions ({@code PERM_compliance.export} and {@code
 * PERM_compliance.erase}) seeded in V29 and assigned exclusively to the ADMIN role. Every
 * invocation is recorded in the audit log via {@link ComplianceService}.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "compliance", description = "CCPA data export and erasure endpoints — PRD §24.5")
public class ComplianceController {

  private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

  private final ComplianceService complianceService;

  public ComplianceController(ComplianceService complianceService) {
    this.complianceService = complianceService;
  }

  /**
   * Exports all tenant data as a ZIP archive containing {@code users.json}, {@code jobs.json},
   * {@code customers.json}, and {@code audit_log.json}.
   *
   * @param authentication the authenticated Admin caller
   * @return 200 with the ZIP payload; {@code Content-Disposition: attachment}
   * @throws IOException if ZIP serialisation fails (propagated as 500)
   */
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('PERM_compliance.export')")
  @Operation(
      summary = "Export all tenant data (CCPA)",
      description =
          "Admin-only. Returns a ZIP archive with users.json, jobs.json, customers.json, and"
              + " audit_log.json for the requesting tenant. Every call is audit-logged.")
  public ResponseEntity<byte[]> export(Authentication authentication) throws IOException {
    UUID tenantId = tenantIdFrom(authentication);
    UUID actorUserId = actorUserIdFrom(authentication);

    byte[] zipBytes = complianceService.exportTenantData(tenantId, actorUserId);

    String filename = "export-" + tenantId + "-" + LocalDate.now() + ".zip";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_ZIP);
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    headers.setContentLength(zipBytes.length);

    return ResponseEntity.ok().headers(headers).body(zipBytes);
  }

  /**
   * Anonymises a customer's PII (CCPA right-to-erasure).
   *
   * @param id the customer UUID to erase
   * @param authentication the authenticated Admin caller
   * @return 204 No Content on success
   */
  @PostMapping("/erase-customer/{id}")
  @PreAuthorize("hasAuthority('PERM_compliance.erase')")
  @Operation(
      summary = "Erase customer PII (CCPA)",
      description =
          "Admin-only. Replaces name, phone, email, and address fields with [REDACTED] and"
              + " soft-archives the customer. Linked jobs are preserved for referential integrity."
              + " Every call is audit-logged.")
  public ResponseEntity<Void> eraseCustomer(@PathVariable UUID id, Authentication authentication) {
    UUID tenantId = tenantIdFrom(authentication);
    UUID actorUserId = actorUserIdFrom(authentication);

    complianceService.eraseCustomer(tenantId, id, actorUserId);
    return ResponseEntity.noContent().build();
  }

  private UUID tenantIdFrom(Authentication authentication) {
    JwtAuthenticationToken jwt = (JwtAuthenticationToken) authentication;
    return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
  }

  private UUID actorUserIdFrom(Authentication authentication) {
    JwtAuthenticationToken jwt = (JwtAuthenticationToken) authentication;
    return UUID.fromString(jwt.getToken().getSubject());
  }
}
