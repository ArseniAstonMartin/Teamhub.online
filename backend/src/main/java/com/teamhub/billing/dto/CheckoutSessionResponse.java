package com.teamhub.billing.dto;

/** Response body for POST /api/v1/billing/checkout/{priceKey} (PRD §17.2, TASK-061). */
public record CheckoutSessionResponse(String url) {}
