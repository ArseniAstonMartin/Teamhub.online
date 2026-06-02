-- Password-reset token table (PRD §6.3, TASK-020).
-- Accessed by the app's normal connection before a tenant context is established,
-- so no RLS policy is applied.

CREATE TABLE password_reset_tokens (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id),
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_token_hash
    ON password_reset_tokens (token_hash);

CREATE INDEX idx_password_reset_tokens_user_created
    ON password_reset_tokens (user_id, created_at);
