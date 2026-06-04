package com.teamhub.push;

import java.util.Map;

/**
 * Push message envelope sent to the Expo Push HTTP V2 API (PRD §15.2).
 *
 * @param to Expo push token of the target device
 * @param title notification title displayed by the OS
 * @param body notification body text
 * @param data arbitrary deep-link parameters passed to the mobile app
 * @param channelId Android notification channel identifier
 */
public record PushMessage(
    String to, String title, String body, Map<String, Object> data, String channelId) {

  public PushMessage(String to, String title, String body, Map<String, Object> data) {
    this(to, title, body, data, "default");
  }
}
