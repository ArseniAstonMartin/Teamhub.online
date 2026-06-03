package com.teamhub.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamhub.audit.AsyncAuditService;
import com.teamhub.audit.AuditLog;
import com.teamhub.audit.AuditLogRepository;
import com.teamhub.auth.UserRepository;
import com.teamhub.auth.dto.UserResponse;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerNotFoundException;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.customer.dto.CustomerResponse;
import com.teamhub.job.JobRepository;
import com.teamhub.job.dto.JobResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CCPA data export and customer erasure (PRD §24.5, TASK-071).
 *
 * <p>Both operations are Admin-only and are audit-logged via {@link AsyncAuditService}. The export
 * ZIP contains four JSON files: {@code users.json}, {@code jobs.json}, {@code customers.json}, and
 * {@code audit_log.json}. The erasure replaces PII fields with {@code "[REDACTED]"} and sets {@code
 * archivedAt} to preserve referential integrity.
 */
@Service
public class ComplianceService {

  private static final String REDACTED = "[REDACTED]";

  /**
   * Null-island point (0°, 0°) used to overwrite the geography column during customer erasure. The
   * column is database NOT NULL, so we replace the PII coordinate with a non-identifying sentinel
   * rather than a Java null.
   */
  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  private static final org.locationtech.jts.geom.Point NULL_ISLAND =
      GF.createPoint(new Coordinate(0.0, 0.0));

  private final UserRepository userRepository;

  private final JobRepository jobRepository;

  private final CustomerRepository customerRepository;

  private final AuditLogRepository auditLogRepository;

  private final AsyncAuditService asyncAuditService;

  private final ObjectMapper objectMapper;

  public ComplianceService(
      UserRepository userRepository,
      JobRepository jobRepository,
      CustomerRepository customerRepository,
      AuditLogRepository auditLogRepository,
      AsyncAuditService asyncAuditService,
      ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
    this.customerRepository = customerRepository;
    this.auditLogRepository = auditLogRepository;
    this.asyncAuditService = asyncAuditService;
    this.objectMapper = objectMapper;
  }

  /**
   * Exports all tenant data as a ZIP archive containing four JSON files.
   *
   * @param tenantId the tenant whose data is exported
   * @param actorUserId the Admin user performing the export (for audit)
   * @return raw bytes of the ZIP archive
   * @throws IOException if ZIP serialisation fails
   */
  @Transactional(readOnly = true)
  public byte[] exportTenantData(UUID tenantId, UUID actorUserId) throws IOException {
    List<UserResponse> users =
        userRepository.findAllByTenantId(tenantId).stream().map(UserResponse::from).toList();
    List<JobResponse> jobs =
        jobRepository.findAllByTenantIdAndArchivedAtIsNull(tenantId).stream()
            .map(JobResponse::from)
            .toList();
    List<CustomerResponse> customers =
        customerRepository.findAllByTenantIdAndArchivedAtIsNull(tenantId).stream()
            .map(c -> CustomerResponse.from(c, false))
            .toList();
    List<AuditLog> auditEntries = auditLogRepository.findAllByTenantId(tenantId);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(baos)) {
      addEntry(zip, "users.json", users);
      addEntry(zip, "jobs.json", jobs);
      addEntry(zip, "customers.json", customers);
      addEntry(zip, "audit_log.json", auditEntries);
    }

    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(actorUserId)
            .action("compliance.data_export")
            .resourceType("tenant")
            .resourceId(tenantId)
            .build());

    return baos.toByteArray();
  }

  /**
   * Anonymises a customer's PII in place (CCPA right-to-erasure, PRD §24.5).
   *
   * <p>The customer row is retained for referential integrity — its {@code id} remains valid for
   * any linked jobs. PII fields are replaced with {@code "[REDACTED]"}, {@code location} is nulled,
   * and {@code archivedAt} is set to the current time so the record is excluded from normal
   * queries.
   *
   * @param tenantId the tenant the customer belongs to
   * @param customerId the customer to erase
   * @param actorUserId the Admin user performing the erasure (for audit)
   * @throws CustomerNotFoundException if no matching active customer is found
   */
  @Transactional
  public void eraseCustomer(UUID tenantId, UUID customerId, UUID actorUserId) {
    Customer customer =
        customerRepository
            .findByIdAndTenantId(customerId, tenantId)
            .orElseThrow(() -> new CustomerNotFoundException(customerId));

    customer.setName(REDACTED);
    customer.setPhone(REDACTED);
    customer.setEmail(REDACTED);
    customer.setAddressLine(REDACTED);
    customer.setCity(REDACTED);
    customer.setState(REDACTED);
    customer.setZip(REDACTED);
    customer.setLocation(NULL_ISLAND);
    customer.setArchivedAt(Instant.now());

    customerRepository.save(customer);

    asyncAuditService.recordAsync(
        AuditLog.builder()
            .tenantId(tenantId)
            .actorUserId(actorUserId)
            .action("compliance.customer_erased")
            .resourceType("customer")
            .resourceId(customerId)
            .build());
  }

  private void addEntry(ZipOutputStream zip, String name, Object data) throws IOException {
    byte[] bytes = objectMapper.writeValueAsBytes(data);
    zip.putNextEntry(new ZipEntry(name));
    zip.write(bytes);
    zip.closeEntry();
  }
}
