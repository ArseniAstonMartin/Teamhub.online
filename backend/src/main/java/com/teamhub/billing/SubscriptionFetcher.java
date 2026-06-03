package com.teamhub.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;

/**
 * Thin abstraction over {@link Subscription#retrieve(String, RequestOptions)} to enable mocking in
 * integration tests that call {@code @Async} methods (TASK-066).
 *
 * <p>The production bean is registered by {@link StripeConfig} and simply delegates to the Stripe
 * SDK. Tests supply a {@code @MockitoBean} replacement that is thread-safe and therefore works
 * correctly across the async executor boundary.
 */
@FunctionalInterface
interface SubscriptionFetcher {

  /**
   * Retrieves a Stripe subscription by ID.
   *
   * @param subscriptionId the Stripe subscription ID (e.g. {@code sub_...})
   * @param requestOptions per-request options (API version, idempotency key, etc.)
   * @return the retrieved {@link Subscription}
   * @throws StripeException on any Stripe API error
   */
  Subscription fetch(String subscriptionId, RequestOptions requestOptions) throws StripeException;
}
