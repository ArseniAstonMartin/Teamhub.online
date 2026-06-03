package com.teamhub.billing;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Stripe Java SDK with the API key from {@link StripePriceProperties} (TASK-060).
 *
 * <p>{@link Stripe#apiKey} is a global thread-safe field set once at startup. All subsequent SDK
 * calls in the JVM inherit it automatically, matching the SDK's recommended integration pattern.
 */
@Configuration
@EnableConfigurationProperties(StripePriceProperties.class)
public class StripeConfig {

  private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

  private final StripePriceProperties properties;

  public StripeConfig(StripePriceProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void configureStripe() {
    Stripe.apiKey = properties.secretKey();
    log.info("Stripe SDK configured (key prefix={})", properties.secretKey().substring(0, 7));
  }
}
