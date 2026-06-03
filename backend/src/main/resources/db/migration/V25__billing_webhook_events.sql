-- V25: Stripe webhook idempotency table + tenant billing state columns (TASK-063, PRD §17.3).
--
-- stripe_webhook_events: one row per processed Stripe event id — prevents double-handling on
-- retries. Not tenant-scoped (events are global); no RLS required.
--
-- current_period_end: subscription billing-period boundary stored on tenant for quota enforcement.
-- payment_failed_at: set when invoice.payment_failed fires; cleared on successful renewal.

-- ---------------------------------------------------------------------------
-- Webhook event deduplication table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stripe_webhook_events (
    id           TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stripe_webhook_events_pkey PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- Tenant billing state columns
-- ---------------------------------------------------------------------------
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS current_period_end  TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS payment_failed_at   TIMESTAMPTZ;
