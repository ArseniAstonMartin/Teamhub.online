package com.teamhub.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Logback converter that redacts PII from log messages (PRD §24.6).
 *
 * <p>Registered as conversion word {@code %redact} in {@code logback-spring.xml} for the local
 * human-readable profile. The same static {@link #redact(String)} method is reused by {@link
 * PiiRedactingMessageProvider} in the prod JSON profile so both encoders apply identical masking.
 *
 * <p>Masking rules:
 *
 * <ul>
 *   <li>Email: {@code user@example.com} → {@code ***@example.com}
 *   <li>E.164 phone: {@code +15551234567} → {@code +1*******4567} (keeps {@code +} + country code +
 *       last 4 digits of full digit string; middle is always 7 asterisks to hide length)
 * </ul>
 */
public class PiiRedactingConverter extends ClassicConverter {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[a-zA-Z0-9._%+\\-]+@([a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})");

  /**
   * Matches E.164 numbers: {@code +} followed by at least 6 digits (1 CC digit + at least 1 middle
   * digit + exactly 4 last digits). Numbers shorter than this (e.g. {@code +123}) are left
   * unchanged.
   */
  private static final Pattern PHONE_PATTERN = Pattern.compile("\\+(\\d)(\\d+)(\\d{4})");

  /**
   * Redacts PII in {@code message}. Safe to call with {@code null} (returns {@code null}).
   *
   * @param message raw log message
   * @return message with emails and E.164 phones masked
   */
  public static String redact(String message) {
    if (message == null) {
      return null;
    }
    String result = EMAIL_PATTERN.matcher(message).replaceAll("***@$1");
    result = PHONE_PATTERN.matcher(result).replaceAll("+$1*******$3");
    return result;
  }

  @Override
  public String convert(ILoggingEvent event) {
    return redact(event.getFormattedMessage());
  }
}
