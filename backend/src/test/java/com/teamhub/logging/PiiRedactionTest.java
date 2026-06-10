package com.teamhub.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for PII redaction rules (PRD §24.6, TASK-013 acceptance criterion 3). */
@Tag("unit")
class PiiRedactionTest {

  private final PiiRedactingConverter converter = new PiiRedactingConverter();

  // ---- email masking -------------------------------------------------------------------------

  @Test
  void email_isMasked() {
    assertThat(PiiRedactingConverter.redact("user@example.com")).isEqualTo("***@example.com");
  }

  @Test
  void emailWithSubdomain_isMasked() {
    assertThat(PiiRedactingConverter.redact("user@sub.example.com"))
        .isEqualTo("***@sub.example.com");
  }

  // ---- phone masking -------------------------------------------------------------------------

  @Test
  void e164Phone_isMasked() {
    assertThat(PiiRedactingConverter.redact("+15551234567")).isEqualTo("+1*******4567");
  }

  @Test
  void shortPhoneNotMatchingE164_isUnchanged() {
    // +123 has only 3 digits — below E.164 minimum; must not be altered.
    assertThat(PiiRedactingConverter.redact("+123")).isEqualTo("+123");
  }

  // ---- no PII --------------------------------------------------------------------------------

  @Test
  void stringWithNoPii_isUnchanged() {
    String message = "Normal log message without any PII data";
    assertThat(PiiRedactingConverter.redact(message)).isEqualTo(message);
  }

  // ---- multi-word string with both email and phone -------------------------------------------

  @Test
  void stringWithEmailAndPhone_bothAreMasked() {
    String input = "Customer user@example.com called from +15551234567 about their order";
    String expected = "Customer ***@example.com called from +1*******4567 about their order";
    assertThat(PiiRedactingConverter.redact(input)).isEqualTo(expected);
  }

  // ---- converter delegation ------------------------------------------------------------------

  @Test
  void convert_delegatesToRedact() {
    ILoggingEvent event = mock(ILoggingEvent.class);
    when(event.getFormattedMessage()).thenReturn("Contact: user@example.com, phone: +15551234567");

    assertThat(converter.convert(event))
        .isEqualTo("Contact: ***@example.com, phone: +1*******4567");
  }
}
