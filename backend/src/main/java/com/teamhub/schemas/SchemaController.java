package com.teamhub.schemas;

import com.teamhub.audit.AuditLog;
import com.teamhub.audit.AuditLogService;
import com.teamhub.schemas.dto.PublishSchemaRequest;
import com.teamhub.schemas.dto.SchemaResponse;
import com.teamhub.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only schema editing endpoints (PRD §5.3, §8.2, TASK-041).
 *
 * <p>All operations require {@code PERM_schema.edit}, which is seeded for the ADMIN role only (V4
 * migration). DISPATCHER and TECHNICIAN callers receive a 403 FORBIDDEN response.
 *
 * <p>No DELETE endpoint is registered. An attempt to {@code DELETE /api/v1/schemas/{id}} returns
 * the default Spring 404 (no handler found), enforcing the append-only invariant of the {@code
 * appointment_schemas} table (PRD §8.2).
 *
 * <p>Every write operation ({@code POST} and {@code PATCH}) emits an audit row with {@code
 * action=schema.update}, {@code resourceType=appointment_schema} via {@link AuditLogService} (PRD
 * §19, TASK-027).
 */
@RestController
@RequestMapping("/api/v1/schemas")
@PreAuthorize("hasAuthority('PERM_schema.edit')")
@Tag(name = "schemas", description = "Appointment-schema versioning endpoints — PRD §5.3, §8.2")
public class SchemaController {

  private final SchemaService schemaService;

  private final AuditLogService auditLogService;

  public SchemaController(SchemaService schemaService, AuditLogService auditLogService) {
    this.schemaService = schemaService;
    this.auditLogService = auditLogService;
  }

  /**
   * Lists all schema versions for the current tenant, ordered by name ascending then version
   * descending (newest version first within each schema name).
   */
  @GetMapping
  @Operation(
      summary = "List schema versions",
      description = "Returns all schema versions for the tenant, newest version first per name.")
  public List<SchemaResponse> list() {
    return schemaService.listByTenant(TenantContext.get()).stream()
        .map(SchemaResponse::from)
        .toList();
  }

  /**
   * Publishes a new schema version.
   *
   * <p>Validates field definitions (depends_on references, options_by_parent keys), bumps the
   * version counter for the given {@code name} within the tenant, deactivates the previous active
   * version, and inserts the new row as active. Emits a {@code schema.update} audit row.
   *
   * @param request schema name and ordered field definitions
   * @return {@code 201 Created} with the persisted schema
   * @throws SchemaValidationException (422) when field-definition constraints are violated
   */
  @PostMapping
  @Operation(
      summary = "Publish a new schema version",
      description =
          "Inserts a new version row; the previous active version is deactivated automatically.")
  public ResponseEntity<SchemaResponse> publish(
      @Valid @RequestBody PublishSchemaRequest request, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    AppointmentSchema schema =
        schemaService.publishNewVersion(tenantId, request.name(), request.fieldDefinitions());

    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(extractActorId(authentication))
            .action("schema.update")
            .resourceType("appointment_schema")
            .resourceId(schema.getId())
            .build());

    return ResponseEntity.status(HttpStatus.CREATED).body(SchemaResponse.from(schema));
  }

  /**
   * Deactivates a schema version by setting {@code is_active = false}.
   *
   * <p>The row is never deleted — it remains for historical job-validation purposes (PRD §8.2).
   * Emits a {@code schema.update} audit row.
   *
   * @param id the schema row to deactivate
   * @return the updated (now-inactive) schema
   * @throws SchemaNotFoundException (404) when {@code id} is not found within this tenant
   */
  @PatchMapping("/{id}")
  @Operation(
      summary = "Deactivate a schema version",
      description = "Sets is_active=false; the row is preserved for historical job validation.")
  public SchemaResponse deactivate(@PathVariable UUID id, Authentication authentication) {
    UUID tenantId = TenantContext.get();
    AppointmentSchema schema = schemaService.deactivateById(tenantId, id);

    auditLogService.record(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(extractActorId(authentication))
            .action("schema.update")
            .resourceType("appointment_schema")
            .resourceId(schema.getId())
            .build());

    return SchemaResponse.from(schema);
  }

  private static UUID extractActorId(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
      return null;
    }
    String sub = jwtToken.getToken().getSubject();
    if (sub == null || sub.isBlank()) {
      return null;
    }
    return UUID.fromString(sub);
  }
}
