-- Device tokens for Expo push notifications (PRD §15.1, TASK-056).
-- Stores one row per Expo push token; tokens are de-duplicated by expo_token UNIQUE constraint.
-- No RLS: access is enforced at the service layer (authenticated user's own tokens only).

CREATE TYPE device_platform AS ENUM ('IOS', 'ANDROID');

CREATE TABLE device_tokens (
  id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tenant_id     UUID        NOT NULL,
  expo_token    TEXT        NOT NULL,
  platform      device_platform NOT NULL,
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  stale         BOOLEAN     NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_device_tokens_expo_token UNIQUE (expo_token)
);

CREATE INDEX idx_device_tokens_user_tenant  ON device_tokens(user_id, tenant_id);
CREATE INDEX idx_device_tokens_tenant_active ON device_tokens(tenant_id) WHERE stale = false;

GRANT SELECT, INSERT, UPDATE, DELETE ON device_tokens TO teamhub_app;
