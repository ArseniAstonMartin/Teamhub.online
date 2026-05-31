package com.teamhub.customer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamhub.customer.Customer;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for customer endpoints (PRD §11.2, §11.3).
 *
 * <p>The {@code phone} field is masked for TECHNICIAN callers whose role does not grant access to
 * the full number (PRD §11.2). Masking is applied by the controller based on the JWT {@code role}
 * claim; this DTO itself is role-agnostic.
 *
 * <p>Null-valued optional fields (e.g. {@code email}, {@code archivedAt}) are omitted from the JSON
 * response via {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(
    UUID id,
    UUID tenantId,
    String name,
    String companyName,
    String phone,
    String email,
    String addressLine,
    String city,
    String state,
    String zip,
    Double lat,
    Double lon,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {

  /** Builds a response from a {@link Customer} entity, applying phone masking when requested. */
  public static CustomerResponse from(Customer c, boolean maskPhone) {
    String phone = maskPhone ? maskPhone(c.getPhone()) : c.getPhone();
    Double lon = c.getLocation() != null ? c.getLocation().getX() : null;
    Double lat = c.getLocation() != null ? c.getLocation().getY() : null;
    return new CustomerResponse(
        c.getId(),
        c.getTenantId(),
        c.getName(),
        c.getCompanyName(),
        phone,
        c.getEmail(),
        c.getAddressLine(),
        c.getCity(),
        c.getState(),
        c.getZip(),
        lat,
        lon,
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getArchivedAt());
  }

  /**
   * Masks the middle digits of an E.164 phone number, revealing only the leading country-code +
   * area-code prefix and the last four digits.
   *
   * <p>Example: {@code +15033334444} → {@code +1503***4444}
   *
   * <p>TODO (PRD §11.2): Replace this blanket masking with a per-customer jobs-link check once the
   * {@code jobs} table exists. Unmask the phone only when the customer is linked to a job that has
   * been claimed by the requesting technician; mask in all other cases.
   */
  static String maskPhone(String phone) {
    if (phone == null || phone.length() < 9) {
      return phone;
    }
    int keepStart = Math.max(phone.length() - 7, 4);
    return phone.substring(0, keepStart) + "***" + phone.substring(phone.length() - 4);
  }
}
