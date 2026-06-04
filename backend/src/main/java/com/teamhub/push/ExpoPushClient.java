package com.teamhub.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client wrapping the Expo Push HTTP V2 API (PRD §15.2).
 *
 * <p>Splits large token lists into batches of at most {@code maxBatchSize} (Expo hard limit: 100),
 * retries failed requests with exponential backoff, and marks tokens stale when the API returns a
 * {@code DeviceNotRegistered} error.
 */
public class ExpoPushClient {

  private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);

  private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

  private final RestClient restClient;

  private final ObjectMapper objectMapper;

  private final DeviceTokenService deviceTokenService;

  private final int maxBatchSize;

  private final int retryMaxAttempts;

  private final long retryBackoffMs;

  public ExpoPushClient(
      RestClient restClient,
      ObjectMapper objectMapper,
      DeviceTokenService deviceTokenService,
      ExpoPushProperties props) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.deviceTokenService = deviceTokenService;
    this.maxBatchSize = props.maxBatchSize();
    this.retryMaxAttempts = props.retryMaxAttempts();
    this.retryBackoffMs = props.retryBackoffMs();
  }

  /**
   * Sends {@code messages} to the Expo Push API, splitting into batches of at most {@code
   * maxBatchSize} messages per request.
   *
   * <p>For each ticket whose {@code status="error"} and {@code details.error="DeviceNotRegistered"}
   * the corresponding device token is marked stale so it will be excluded from future fan-out.
   *
   * @param messages list of push messages to deliver
   * @return flat list of all tickets returned by Expo, in message order
   */
  public List<PushTicket> sendBatch(List<PushMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return Collections.emptyList();
    }

    List<PushTicket> allTickets = new ArrayList<>();

    for (int offset = 0; offset < messages.size(); offset += maxBatchSize) {
      List<PushMessage> batch =
          messages.subList(offset, Math.min(offset + maxBatchSize, messages.size()));
      List<PushTicket> tickets = sendWithRetry(batch);
      reconcileTickets(batch, tickets);
      allTickets.addAll(tickets);
    }

    return allTickets;
  }

  private List<PushTicket> sendWithRetry(List<PushMessage> batch) {
    long backoffMs = retryBackoffMs;
    Exception lastException = null;

    for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
      try {
        return doSend(batch);
      } catch (Exception e) {
        lastException = e;
        log.warn("Expo push attempt {}/{} failed: {}", attempt, retryMaxAttempts, e.getMessage());
        if (attempt < retryMaxAttempts) {
          sleep(backoffMs);
          backoffMs *= 2;
        }
      }
    }

    log.error(
        "Expo push failed after {} attempts; dropping batch of {} messages",
        retryMaxAttempts,
        batch.size(),
        lastException);
    return Collections.emptyList();
  }

  private List<PushTicket> doSend(List<PushMessage> batch) {
    String responseBody = restClient.post().body(batch).retrieve().body(String.class);

    return parseTickets(responseBody);
  }

  private List<PushTicket> parseTickets(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode data = root.get("data");
      if (data == null || !data.isArray()) {
        log.warn("Expo push response missing 'data' array: {}", responseBody);
        return Collections.emptyList();
      }
      return objectMapper.convertValue(data, new TypeReference<List<PushTicket>>() {});
    } catch (Exception e) {
      log.error("Failed to parse Expo push response: {}", responseBody, e);
      return Collections.emptyList();
    }
  }

  private void reconcileTickets(List<PushMessage> batch, List<PushTicket> tickets) {
    for (int i = 0; i < Math.min(batch.size(), tickets.size()); i++) {
      PushTicket ticket = tickets.get(i);
      if ("error".equals(ticket.status()) && ticket.details() != null) {
        Object errorCode = ticket.details().get("error");
        if (DEVICE_NOT_REGISTERED.equals(errorCode)) {
          String expoToken = batch.get(i).to();
          log.info("Marking stale device token: {}", expoToken);
          deviceTokenService.markStale(expoToken);
        }
      }
    }
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  /** Returns the configured max batch size (exposed for testing). */
  int getMaxBatchSize() {
    return maxBatchSize;
  }
}
