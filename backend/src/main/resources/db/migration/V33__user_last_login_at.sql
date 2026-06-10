-- V33: User last login timestamp (TASK-129).
--
-- Nullable last_login_at on users; set on password login and OAuth success.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at timestamptz;
