package com.teamhub.push;

import java.util.Map;

/**
 * Ticket returned by the Expo Push HTTP V2 API for each message in a batch (PRD §15.2).
 *
 * <p>A ticket with {@code status="ok"} carries the Expo receipt {@code id}. A ticket with {@code
 * status="error"} carries a {@code details} map whose {@code error} key may equal {@code
 * "DeviceNotRegistered"}, indicating the token should be marked stale.
 *
 * @param status {@code "ok"} or {@code "error"}
 * @param id Expo receipt ID; present only when {@code status="ok"}
 * @param details error details; present only when {@code status="error"}
 */
public record PushTicket(String status, String id, Map<String, Object> details) {}
