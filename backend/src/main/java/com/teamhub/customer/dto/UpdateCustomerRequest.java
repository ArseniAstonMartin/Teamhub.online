package com.teamhub.customer.dto;

/**
 * Request body for {@code PATCH /api/v1/customers/{id}} (PRD §11.3).
 *
 * <p>All fields are optional; only non-null values are written to the existing record. The phone
 * field is intentionally absent — phone changes require a new upsert via {@code POST /customers} to
 * preserve the upsert-on-phone identity semantics.
 */
public record UpdateCustomerRequest(
    String name,
    String email,
    String companyName,
    String addressLine,
    String city,
    String state,
    String zip,
    Double lat,
    Double lon) {}
