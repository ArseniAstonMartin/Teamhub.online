package com.teamhub.billing;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link StripeWebhookEvent} (TASK-063, PRD §17.3). */
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {}
