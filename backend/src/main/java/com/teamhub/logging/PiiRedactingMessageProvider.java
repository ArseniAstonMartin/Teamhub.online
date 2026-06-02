package com.teamhub.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

/**
 * Logstash JSON message provider that applies PII redaction (PRD §24.6) before writing the {@code
 * message} field in the structured JSON log line.
 *
 * <p>Referenced in the {@code prod} appender inside {@code logback-spring.xml}. Delegates masking
 * to {@link PiiRedactingConverter#redact(String)} to keep the rules in one place.
 */
public class PiiRedactingMessageProvider extends MessageJsonProvider {

  @Override
  public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    generator.writeStringField(
        getFieldName(), PiiRedactingConverter.redact(event.getFormattedMessage()));
  }
}
